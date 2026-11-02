package com.project.billing.subscription.domain

/** Port to the message broker. Throws when the event could not be handed over. */
interface EventPublisher {
    fun publish(event: OutboxEvent)
}
