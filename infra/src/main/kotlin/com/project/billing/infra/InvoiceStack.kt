package com.project.billing.infra

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.Duration
import software.amazon.awscdk.RemovalPolicy
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.dynamodb.Attribute
import software.amazon.awscdk.services.dynamodb.AttributeType
import software.amazon.awscdk.services.dynamodb.BillingMode
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps
import software.amazon.awscdk.services.dynamodb.Table
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource
import software.amazon.awscdk.services.logs.RetentionDays
import software.amazon.awscdk.services.s3.BlockPublicAccess
import software.amazon.awscdk.services.s3.Bucket
import software.amazon.awscdk.services.s3.BucketEncryption
import software.amazon.awscdk.services.sqs.Queue
import software.constructs.Construct

/**
 * generate-invoice Lambda: SQS -> invoice document in S3 + metadata in DynamoDB.
 * The zip is built by `services/invoice-lambda` (`gradlew buildZip`) before synth.
 */
class InvoiceStack(
    scope: Construct,
    id: String,
    props: StackProps,
    invoiceGenerationQueue: Queue,
    lambdaZipPath: String,
) : Stack(scope, id, props) {

    val bucket: Bucket = Bucket.Builder.create(this, "InvoiceBucket")
        .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
        .encryption(BucketEncryption.S3_MANAGED)
        .enforceSsl(true)
        .removalPolicy(RemovalPolicy.DESTROY)
        .autoDeleteObjects(true)
        .build()

    val table: Table = Table.Builder.create(this, "InvoiceTable")
        .tableName("invoices")
        .partitionKey(Attribute.builder().name("customerId").type(AttributeType.STRING).build())
        .sortKey(Attribute.builder().name("subscriptionId").type(AttributeType.STRING).build())
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build()

    init {
        // Lets us list invoices across all customers by status (e.g. every UNPAID one) without a Scan.
        table.addGlobalSecondaryIndex(
            GlobalSecondaryIndexProps.builder()
                .indexName("status-issuedAt-index")
                .partitionKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("issuedAt").type(AttributeType.STRING).build())
                .build(),
        )

        val function = Function.Builder.create(this, "GenerateInvoiceFunction")
            .functionName("generate-invoice")
            .runtime(Runtime.JAVA_21)
            .handler("com.project.billing.invoicelambda.GenerateInvoiceHandler::handleRequest")
            .code(Code.fromAsset(lambdaZipPath))
            .memorySize(512)
            .timeout(Duration.seconds(20))
            .logRetention(RetentionDays.ONE_WEEK)
            .environment(mapOf("INVOICE_BUCKET" to bucket.bucketName, "INVOICE_TABLE" to table.tableName))
            .build()

        // Execution role: only what the code calls (PutObject on the bucket, PutItem/DeleteItem on the table).
        bucket.grantPut(function)
        table.grantWriteData(function)

        function.addEventSource(
            SqsEventSource.Builder.create(invoiceGenerationQueue)
                .batchSize(10)
                .reportBatchItemFailures(true)
                .build(),
        )

        CfnOutput.Builder.create(this, "BucketName").value(bucket.bucketName).build()
        CfnOutput.Builder.create(this, "TableName").value(table.tableName).build()
    }
}
