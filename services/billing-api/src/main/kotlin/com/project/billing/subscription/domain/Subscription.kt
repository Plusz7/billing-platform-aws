package com.project.billing.subscription.domain

import java.time.Instant
import java.util.UUID

enum class SubscriptionStatus { ACTIVE, CANCELLED }

class Subscription(
    val id: UUID,
    val customerId: String,
    val plan: String,
    status: SubscriptionStatus,
    val startedAt: Instant,
    cancelledAt: Instant? = null,
) {
    var status: SubscriptionStatus = status
        private set
    var cancelledAt: Instant? = cancelledAt
        private set

    init {
        require(customerId.isNotBlank()) { "customerId must not be blank" }
        require(plan.isNotBlank()) { "plan must not be blank" }
    }

    fun cancel(at: Instant) {
        check(status == SubscriptionStatus.ACTIVE) { "Subscription $id is already cancelled" }
        status = SubscriptionStatus.CANCELLED
        cancelledAt = at
    }

    companion object {
        fun start(customerId: String, plan: String, at: Instant) =
            Subscription(UUID.randomUUID(), customerId, plan, SubscriptionStatus.ACTIVE, at)
    }
}
