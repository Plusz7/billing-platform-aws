package com.project.billing.subscription

import java.time.Instant
import java.util.UUID

data class SubscriptionDto(
    val id: UUID,
    val customerId: String,
    val plan: String,
    val status: String,
    val startedAt: Instant,
    val cancelledAt: Instant?,
)
