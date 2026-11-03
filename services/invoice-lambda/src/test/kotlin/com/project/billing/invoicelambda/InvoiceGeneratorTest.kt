package com.project.billing.invoicelambda

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvoiceGeneratorTest {

    private class InMemoryStore : InvoiceStore {
        val documents = mutableMapOf<String, String>()

        override fun saveIfAbsent(invoice: Invoice, document: String): Boolean =
            documents.putIfAbsent(invoice.documentKey, document) == null
    }

    private val event = SubscriptionStarted("sub-1", "cust-1", "PRO", "2026-11-03T10:00:00Z")
    private val store = InMemoryStore()
    private val generator = InvoiceGenerator(store) { Instant.parse("2026-11-03T10:00:01Z") }

    @Test
    fun `creates an unpaid invoice document for a started subscription`() {
        assertTrue(generator.generate(event))

        val document = store.documents.getValue("invoices/cust-1/sub-1.txt")
        assertTrue(document.contains("Plan: PRO"))
        assertTrue(document.contains("Issued at: 2026-11-03T10:00:01Z"))
    }

    @Test
    fun `a duplicate delivery does not create a second invoice`() {
        assertTrue(generator.generate(event))
        assertFalse(generator.generate(event))

        assertEquals(1, store.documents.size)
    }
}
