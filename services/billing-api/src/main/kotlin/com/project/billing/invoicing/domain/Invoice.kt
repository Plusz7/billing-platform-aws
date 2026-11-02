package com.project.billing.invoicing.domain

import java.time.Instant
import java.util.UUID

enum class InvoiceStatus { ISSUED }

class Invoice(
    val id: UUID,
    val subscriptionId: UUID,
    val customerId: String,
    val plan: String,
    val status: InvoiceStatus,
    val issuedAt: Instant,
) {
    init {
        require(customerId.isNotBlank()) { "customerId must not be blank" }
        require(plan.isNotBlank()) { "plan must not be blank" }
    }

    companion object {
        fun issueFor(subscriptionId: UUID, customerId: String, plan: String, at: Instant) =
            Invoice(UUID.randomUUID(), subscriptionId, customerId, plan, InvoiceStatus.ISSUED, at)
    }
}
