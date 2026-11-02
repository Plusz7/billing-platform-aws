package com.project.billing.subscription.domain

import java.time.Instant
import java.util.UUID

data class SubscriptionStartedEvent(
    val subscriptionId: UUID,
    val customerId: String,
    val plan: String,
    val occurredAt: Instant,
)

data class SubscriptionCancelledEvent(
    val subscriptionId: UUID,
    val customerId: String,
    val occurredAt: Instant,
)
