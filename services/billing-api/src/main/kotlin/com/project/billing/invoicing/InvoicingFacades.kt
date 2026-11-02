package com.project.billing.invoicing

import java.util.UUID

interface InvoiceQueryFacade {
    fun findBySubscription(subscriptionId: UUID): InvoiceDto?
}
