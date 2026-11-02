package com.project.billing.subscription.infrastructure.messaging

import com.project.billing.subscription.domain.EventPublisher
import com.project.billing.subscription.domain.OutboxEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue

/** Publishes to the SNS topic; `eventType` is a message attribute so subscribers can use filter policies. */
@Component
@ConditionalOnExpression("!'\${billing.events.topic-arn:}'.isEmpty()")
internal class SnsEventPublisher(
    private val sns: SnsClient,
    @param:Value("\${billing.events.topic-arn}") private val topicArn: String,
) : EventPublisher {

    override fun publish(event: OutboxEvent) {
        sns.publish {
            it.topicArn(topicArn)
                .message(event.payload)
                .messageAttributes(
                    mapOf(
                        "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(event.type).build(),
                    ),
                )
        }
    }
}
