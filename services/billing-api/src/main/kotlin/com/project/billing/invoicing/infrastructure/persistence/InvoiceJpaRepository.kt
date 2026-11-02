package com.project.billing.invoicing.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface InvoiceJpaRepository : JpaRepository<InvoiceJpaEntity, UUID> {
    fun findBySubscriptionId(subscriptionId: UUID): InvoiceJpaEntity?
}
