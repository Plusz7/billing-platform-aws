package com.project.billing.invoicing.domain

import java.util.UUID

interface InvoiceRepository {
    fun save(invoice: Invoice): Invoice
    fun findBySubscriptionId(subscriptionId: UUID): Invoice?
}
