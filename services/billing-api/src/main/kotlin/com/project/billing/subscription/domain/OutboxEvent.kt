package com.project.billing.subscription.domain

import java.time.Instant
import java.util.UUID

/** A domain event stored in the same transaction as its aggregate and published to the broker later. */
class OutboxEvent(
    val id: UUID,
    val aggregateId: UUID,
    val type: String,
    val payload: String,
    val createdAt: Instant,
    publishedAt: Instant? = null,
) {
    var publishedAt: Instant? = publishedAt
        private set

    fun markPublished(at: Instant) {
        publishedAt = at
    }

    companion object {
        /** The event type is the simple class name of [event], e.g. `SubscriptionStartedEvent`. */
        fun record(aggregateId: UUID, event: Any, payload: String, at: Instant) =
            OutboxEvent(UUID.randomUUID(), aggregateId, requireNotNull(event::class.simpleName), payload, at)
    }
}
