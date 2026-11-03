package com.project.billing.invoicelambda

import java.time.Instant

data class SubscriptionStarted(
    val subscriptionId: String,
    val customerId: String,
    val plan: String,
    val occurredAt: String,
)

/** Storage port: keeps the generator testable without AWS. */
interface InvoiceStore {
    /** Returns false when an invoice for this subscription already exists (duplicate delivery). */
    fun saveIfAbsent(invoice: Invoice, document: String): Boolean
}

data class Invoice(
    val customerId: String,
    val subscriptionId: String,
    val plan: String,
    val status: String,
    val issuedAt: String,
    val documentKey: String,
)

class InvoiceGenerator(private val store: InvoiceStore, private val clock: () -> Instant = Instant::now) {

    /** Idempotent: SQS standard queues deliver at-least-once, so a repeat must not create a second invoice. */
    fun generate(event: SubscriptionStarted): Boolean {
        val issuedAt = clock().toString()
        val invoice = Invoice(
            customerId = event.customerId,
            subscriptionId = event.subscriptionId,
            plan = event.plan,
            status = STATUS_UNPAID,
            issuedAt = issuedAt,
            documentKey = "invoices/${event.customerId}/${event.subscriptionId}.txt",
        )
        val document = """
            INVOICE
            Customer: ${event.customerId}
            Subscription: ${event.subscriptionId}
            Plan: ${event.plan}
            Issued at: $issuedAt
        """.trimIndent()
        return store.saveIfAbsent(invoice, document)
    }

    companion object {
        const val STATUS_UNPAID = "UNPAID"
    }
}
