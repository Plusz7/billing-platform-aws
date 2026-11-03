package com.project.billing.invoicelambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client

/**
 * Triggered by SQS. Failed messages are reported individually (partial batch response), so the rest of the batch is
 * deleted and only the failures return to the queue and, after maxReceiveCount, go to the DLQ.
 */
class GenerateInvoiceHandler(private val generator: InvoiceGenerator) : RequestHandler<SQSEvent, SQSBatchResponse> {

    // Clients are created once per execution environment, not per invocation.
    constructor() : this(InvoiceGenerator(defaultStore()))

    override fun handleRequest(event: SQSEvent, context: Context): SQSBatchResponse {
        val failures = event.records.mapNotNull { record ->
            try {
                if (record.messageAttributes["eventType"]?.stringValue == "SubscriptionStartedEvent") {
                    val started: SubscriptionStarted = mapper.readValue(record.body)
                    generator.generate(started)
                }
                null
            } catch (e: Exception) {
                context.logger.log("Message ${record.messageId} failed: ${e.message}")
                SQSBatchResponse.BatchItemFailure(record.messageId)
            }
        }
        return SQSBatchResponse(failures)
    }

    private companion object {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun defaultStore(): InvoiceStore {
            val http = UrlConnectionHttpClient.builder()
            return AwsInvoiceStore(
                S3Client.builder().httpClientBuilder(http).build(),
                DynamoDbClient.builder().httpClientBuilder(http).build(),
                requireNotNull(System.getenv("INVOICE_BUCKET")) { "INVOICE_BUCKET is not set" },
                requireNotNull(System.getenv("INVOICE_TABLE")) { "INVOICE_TABLE is not set" },
            )
        }
    }
}
