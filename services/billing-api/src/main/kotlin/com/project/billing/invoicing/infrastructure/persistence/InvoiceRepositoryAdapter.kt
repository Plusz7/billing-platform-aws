package com.project.billing.invoicing.infrastructure.persistence

import com.project.billing.invoicing.domain.Invoice
import com.project.billing.invoicing.domain.InvoiceRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class InvoiceRepositoryAdapter(
    private val jpaRepository: InvoiceJpaRepository,
) : InvoiceRepository {

    override fun save(invoice: Invoice): Invoice = jpaRepository.save(invoice.toEntity()).toDomain()

    override fun findBySubscriptionId(subscriptionId: UUID): Invoice? =
        jpaRepository.findBySubscriptionId(subscriptionId)?.toDomain()

    private fun Invoice.toEntity() = InvoiceJpaEntity(id, subscriptionId, customerId, plan, status, issuedAt)

    private fun InvoiceJpaEntity.toDomain() = Invoice(id, subscriptionId, customerId, plan, status, issuedAt)
}
