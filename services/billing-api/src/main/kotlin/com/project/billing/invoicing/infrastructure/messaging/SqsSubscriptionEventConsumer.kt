package com.project.billing.invoicing.infrastructure.messaging

import com.project.billing.invoicing.application.SubscriptionEventHandler
import com.project.billing.invoicing.application.SubscriptionStarted
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.ObjectMapper

/**
 * Long-polls the invoicing queue on its own thread (a scheduler thread would block the outbox relay).
 * A message is deleted only after it was handled; on failure it stays in the queue, becomes visible again after the
 * visibility timeout and moves to the DLQ once the queue's maxReceiveCount is exceeded.
 */
@Component
@ConditionalOnExpression("!'\${billing.invoicing.queue-url:}'.isEmpty()")
internal class SqsSubscriptionEventConsumer(
    private val sqs: SqsClient,
    private val handler: SubscriptionEventHandler,
    private val objectMapper: ObjectMapper,
    @param:Value("\${billing.invoicing.queue-url}") private val queueUrl: String,
    @param:Value("\${billing.invoicing.poll-wait-seconds}") private val waitSeconds: Int,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var thread: Thread? = null

    override fun start() {
        thread = Thread(::pollLoop, "sqs-invoicing-consumer").apply { start() }
    }

    override fun stop() {
        thread?.let {
            it.interrupt()
            it.join(STOP_TIMEOUT_MS)
        }
        thread = null
    }

    override fun isRunning(): Boolean = thread?.isAlive == true

    private fun pollLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val messages = sqs.receiveMessage {
                    it.queueUrl(queueUrl)
                        .maxNumberOfMessages(BATCH_SIZE)
                        .waitTimeSeconds(waitSeconds)
                        .messageAttributeNames(EVENT_TYPE)
                }.messages()
                messages.forEach(::process)
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) return
                log.warn("Receiving from {} failed, backing off", queueUrl, e)
                try {
                    Thread.sleep(BACKOFF_MS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun process(message: Message) {
        try {
            when (val type = message.messageAttributes()[EVENT_TYPE]?.stringValue()) {
                "SubscriptionStartedEvent" ->
                    handler.onSubscriptionStarted(objectMapper.readValue(message.body(), SubscriptionStarted::class.java))
                else -> log.debug("Ignoring event of type {}", type)
            }
            sqs.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
        } catch (e: Exception) {
            log.warn("Processing message {} failed, leaving it for redelivery", message.messageId(), e)
        }
    }

    private companion object {
        const val EVENT_TYPE = "eventType"
        const val BATCH_SIZE = 10
        const val BACKOFF_MS = 5_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
