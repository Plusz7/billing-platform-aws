package com.project.billing.invoicing.infrastructure.messaging

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
@ConditionalOnExpression("!'\${billing.invoicing.queue-url:}'.isEmpty()")
internal class SqsConfig {
    // Region and credentials come from the default chain (on Fargate: AWS_REGION and the task role).
    // The endpoint override is only set for LocalStack.
    @Bean(destroyMethod = "close")
    fun sqsClient(@Value("\${billing.aws.endpoint-override:}") endpointOverride: String): SqsClient =
        SqsClient.builder()
            .apply { if (endpointOverride.isNotBlank()) endpointOverride(URI.create(endpointOverride)) }
            .build()
}
