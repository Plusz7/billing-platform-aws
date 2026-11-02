package com.project.billing.invoicing.application

import com.project.billing.invoicing.InvoiceDto
import com.project.billing.invoicing.InvoiceQueryFacade
import com.project.billing.invoicing.domain.Invoice
import com.project.billing.invoicing.domain.InvoiceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
internal class InvoicingService(
    private val repository: InvoiceRepository,
) : SubscriptionEventHandler, InvoiceQueryFacade {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onSubscriptionStarted(event: SubscriptionStarted) {
        if (repository.findBySubscriptionId(event.subscriptionId) != null) {
            log.info("Invoice for subscription {} already issued, skipping duplicate delivery", event.subscriptionId)
            return
        }
        // A concurrent duplicate loses on the unique index; the message is then redelivered and skipped above.
        repository.save(Invoice.issueFor(event.subscriptionId, event.customerId, event.plan, event.occurredAt))
    }

    @Transactional(readOnly = true)
    override fun findBySubscription(subscriptionId: UUID): InvoiceDto? =
        repository.findBySubscriptionId(subscriptionId)?.toDto()

    private fun Invoice.toDto() = InvoiceDto(id, subscriptionId, customerId, plan, status.name, issuedAt)
}
