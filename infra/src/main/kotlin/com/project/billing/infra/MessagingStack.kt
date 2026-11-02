package com.project.billing.infra

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.sns.Topic
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription
import software.amazon.awscdk.services.sqs.DeadLetterQueue
import software.amazon.awscdk.services.sqs.Queue
import software.constructs.Construct

/** SNS topic fanning out subscription events to an SQS queue; failed messages end up in a DLQ. */
class MessagingStack(scope: Construct, id: String, props: StackProps) : Stack(scope, id, props) {

    val topic: Topic = Topic.Builder.create(this, "SubscriptionEventsTopic")
        .topicName("subscription-events")
        .build()

    val deadLetterQueue: Queue = Queue.Builder.create(this, "InvoicingDlq")
        .queueName("invoicing-events-dlq")
        .retentionPeriod(Duration.days(14))
        .build()

    val queue: Queue = Queue.Builder.create(this, "InvoicingQueue")
        .queueName("invoicing-events")
        // Must exceed the consumer's processing time, otherwise a message is delivered twice.
        .visibilityTimeout(Duration.seconds(60))
        // Long polling: ReceiveMessage waits up to 20 s instead of returning empty responses.
        .receiveMessageWaitTime(Duration.seconds(20))
        .deadLetterQueue(
            DeadLetterQueue.builder().queue(deadLetterQueue).maxReceiveCount(3).build(),
        )
        .build()

    init {
        // Creates the subscription and the queue policy that lets this topic send to the queue.
        topic.addSubscription(SqsSubscription.Builder.create(queue).rawMessageDelivery(true).build())

        CfnOutput.Builder.create(this, "TopicArn").value(topic.topicArn).build()
        CfnOutput.Builder.create(this, "QueueUrl").value(queue.queueUrl).build()
        CfnOutput.Builder.create(this, "DlqUrl").value(deadLetterQueue.queueUrl).build()
    }
}
