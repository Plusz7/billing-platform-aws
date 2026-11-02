package com.project.billing.invoicing

import java.time.Instant
import java.util.UUID

data class InvoiceDto(
    val id: UUID,
    val subscriptionId: UUID,
    val customerId: String,
    val plan: String,
    val status: String,
    val issuedAt: Instant,
)
