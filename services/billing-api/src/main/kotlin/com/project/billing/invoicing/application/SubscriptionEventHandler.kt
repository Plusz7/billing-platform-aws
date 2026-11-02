package com.project.billing.invoicing.application

import java.time.Instant
import java.util.UUID

/** Invoicing's own view of the subscription events it reacts to; it does not share classes with the subscription BC. */
data class SubscriptionStarted(
    val subscriptionId: UUID,
    val customerId: String,
    val plan: String,
    val occurredAt: Instant,
)

/** Entry point for messages arriving from the broker. Handlers must be idempotent: delivery is at-least-once. */
interface SubscriptionEventHandler {
    fun onSubscriptionStarted(event: SubscriptionStarted)
}
