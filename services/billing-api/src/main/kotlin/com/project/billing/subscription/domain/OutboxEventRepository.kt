package com.project.billing.subscription.domain

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent

    /**
     * Oldest unpublished events, locked for the current transaction. Rows locked by another
     * transaction are skipped, so several instances can poll without publishing the same event twice.
     */
    fun lockUnpublished(limit: Int): List<OutboxEvent>
}
