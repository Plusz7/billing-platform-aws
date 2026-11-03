package com.project.billing.invoicelambda

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.s3.S3Client

/**
 * The DynamoDB conditional put is the idempotency guard and runs first; the S3 object is written only for a new
 * invoice. If the S3 write fails, the item is removed again so the redelivered message can start over.
 */
class AwsInvoiceStore(
    private val s3: S3Client,
    private val dynamo: DynamoDbClient,
    private val bucket: String,
    private val table: String,
) : InvoiceStore {

    override fun saveIfAbsent(invoice: Invoice, document: String): Boolean {
        try {
            dynamo.putItem {
                it.tableName(table)
                    .item(
                        mapOf(
                            "customerId" to s(invoice.customerId),
                            "subscriptionId" to s(invoice.subscriptionId),
                            "plan" to s(invoice.plan),
                            "status" to s(invoice.status),
                            "issuedAt" to s(invoice.issuedAt),
                            "documentKey" to s(invoice.documentKey),
                        ),
                    )
                    .conditionExpression("attribute_not_exists(subscriptionId)")
            }
        } catch (_: ConditionalCheckFailedException) {
            return false
        }
        try {
            s3.putObject({ it.bucket(bucket).key(invoice.documentKey) }, RequestBody.fromString(document))
        } catch (e: Exception) {
            dynamo.deleteItem {
                it.tableName(table).key(
                    mapOf("customerId" to s(invoice.customerId), "subscriptionId" to s(invoice.subscriptionId)),
                )
            }
            throw e
        }
        return true
    }

    private fun s(value: String) = AttributeValue.fromS(value)
}
