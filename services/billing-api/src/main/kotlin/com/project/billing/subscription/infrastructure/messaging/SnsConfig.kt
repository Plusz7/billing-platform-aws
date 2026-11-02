package com.project.billing.subscription.infrastructure.messaging

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sns.SnsClient

@Configuration
@ConditionalOnExpression("!'\${billing.events.topic-arn:}'.isEmpty()")
internal class SnsConfig {
    // Region and credentials come from the default chain (on Fargate: AWS_REGION and the task role).
    @Bean(destroyMethod = "close")
    fun snsClient(): SnsClient = SnsClient.create()
}
