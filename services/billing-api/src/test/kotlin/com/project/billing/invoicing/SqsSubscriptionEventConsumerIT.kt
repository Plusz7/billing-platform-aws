package com.project.billing.invoicing

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
internal class SqsSubscriptionEventConsumerIT {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4.0"))
            .withServices("sqs")

        // The application builds its SqsClient with the default credentials/region chain; point that chain at LocalStack.
        init {
            System.setProperty("aws.accessKeyId", localstack.accessKey)
            System.setProperty("aws.secretAccessKey", localstack.secretKey)
            System.setProperty("aws.region", localstack.region)
        }

        private val sqs: SqsClient by lazy {
            SqsClient.builder()
                .endpointOverride(localstack.endpoint)
                .region(Region.of(localstack.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)),
                )
                .build()
        }

        private val dlqUrl: String by lazy { sqs.createQueue { it.queueName("invoicing-events-dlq") }.queueUrl() }

        // Mirrors MessagingStack: DLQ with a redrive policy; short timeouts so the test does not wait long.
        private val queueUrl: String by lazy {
            val dlqArn = sqs.getQueueAttributes {
                it.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN)
            }.attributes()[QueueAttributeName.QUEUE_ARN]
            sqs.createQueue {
                it.queueName("invoicing-events")
                    .attributes(
                        mapOf(
                            QueueAttributeName.VISIBILITY_TIMEOUT to "1",
                            QueueAttributeName.REDRIVE_POLICY to
                                """{"deadLetterTargetArn":"$dlqArn","maxReceiveCount":"2"}""",
                        ),
                    )
            }.queueUrl()
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("billing.invoicing.queue-url") { queueUrl }
            registry.add("billing.invoicing.poll-wait-seconds") { "1" }
            registry.add("billing.aws.endpoint-override") { localstack.endpoint.toString() }
        }

        @AfterAll
        @JvmStatic
        fun clearAwsProperties() {
            listOf("aws.accessKeyId", "aws.secretAccessKey", "aws.region").forEach(System::clearProperty)
        }
    }

    @Autowired
    lateinit var invoices: InvoiceQueryFacade

    @Test
    fun `subscription started message issues an invoice`() {
        val subscriptionId = UUID.randomUUID()

        send(startedEvent(subscriptionId, "cust-sqs-1", "PRO"))

        val invoice = awaitInvoice(subscriptionId)
        assertEquals("cust-sqs-1", invoice.customerId)
        assertEquals("PRO", invoice.plan)
        assertEquals("ISSUED", invoice.status)
    }

    @Test
    fun `duplicate delivery of the same event issues only one invoice`() {
        val subscriptionId = UUID.randomUUID()
        val body = startedEvent(subscriptionId, "cust-sqs-2", "BASIC")

        send(body)
        val first = awaitInvoice(subscriptionId)
        send(body)
        // A marker event sent after the duplicate: once it is handled the duplicate has been consumed as well.
        val markerId = UUID.randomUUID()
        send(startedEvent(markerId, "cust-sqs-marker", "BASIC"))
        awaitInvoice(markerId)

        assertEquals(first, invoices.findBySubscription(subscriptionId))
    }

    @Test
    fun `other event types are ignored and removed from the queue`() {
        val subscriptionId = UUID.randomUUID()

        send("""{"subscriptionId":"$subscriptionId","customerId":"cust-sqs-3"}""", type = "SubscriptionCancelledEvent")

        awaitQueueEmpty(queueUrl)
        assertEquals(null, invoices.findBySubscription(subscriptionId))
        assertEquals(0, visibleMessages(dlqUrl))
    }

    @Test
    fun `malformed message does not block the queue and ends up in the DLQ`() {
        send("not json at all")
        val subscriptionId = UUID.randomUUID()
        send(startedEvent(subscriptionId, "cust-sqs-4", "PRO"))

        awaitInvoice(subscriptionId)
        await { visibleMessages(dlqUrl) > 0 }
    }

    private fun startedEvent(subscriptionId: UUID, customerId: String, plan: String) =
        """{"subscriptionId":"$subscriptionId","customerId":"$customerId","plan":"$plan","occurredAt":"${Instant.now()}"}"""

    private fun send(body: String, type: String = "SubscriptionStartedEvent") {
        sqs.sendMessage {
            it.queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(
                    mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(type).build()),
                )
        }
    }

    private fun awaitInvoice(subscriptionId: UUID): InvoiceDto {
        await { invoices.findBySubscription(subscriptionId) != null }
        return assertNotNull(invoices.findBySubscription(subscriptionId))
    }

    private fun awaitQueueEmpty(url: String) = await {
        val attributes = sqs.getQueueAttributes {
            it.queueUrl(url).attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
            )
        }.attributes()
        attributes.values.all { it == "0" }
    }

    // Reads without deleting; the 0-second visibility timeout keeps the message available for other checks.
    private fun visibleMessages(url: String): Int =
        sqs.receiveMessage { it.queueUrl(url).maxNumberOfMessages(10).visibilityTimeout(0) }.messages().size

    private fun await(timeout: Duration = Duration.ofSeconds(30), condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (!condition()) {
            assertTrue(System.nanoTime() < deadline, "Condition not met within $timeout")
            Thread.sleep(200)
        }
    }
}
