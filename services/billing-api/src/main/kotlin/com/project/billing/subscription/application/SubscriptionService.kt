package com.project.billing.subscription.application

import com.project.billing.subscription.SubscriptionCommandFacade
import com.project.billing.subscription.SubscriptionDto
import com.project.billing.subscription.SubscriptionNotFoundException
import com.project.billing.subscription.SubscriptionQueryFacade
import com.project.billing.subscription.domain.OutboxEvent
import com.project.billing.subscription.domain.OutboxEventRepository
import com.project.billing.subscription.domain.Subscription
import com.project.billing.subscription.domain.SubscriptionCancelledEvent
import com.project.billing.subscription.domain.SubscriptionRepository
import com.project.billing.subscription.domain.SubscriptionStartedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
internal class SubscriptionService(
    private val repository: SubscriptionRepository,
    private val outbox: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : SubscriptionCommandFacade, SubscriptionQueryFacade {

    @Transactional
    override fun subscribe(customerId: String, plan: String): SubscriptionDto {
        val at = now()
        val saved = repository.save(Subscription.start(customerId, plan, at))
        record(saved.id, SubscriptionStartedEvent(saved.id, customerId, plan, at), at)
        return saved.toDto()
    }

    @Transactional
    override fun cancel(id: UUID): SubscriptionDto {
        val subscription = repository.findById(id) ?: throw SubscriptionNotFoundException(id)
        val at = now()
        subscription.cancel(at)
        val saved = repository.save(subscription)
        record(id, SubscriptionCancelledEvent(id, saved.customerId, at), at)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    override fun get(id: UUID): SubscriptionDto =
        (repository.findById(id) ?: throw SubscriptionNotFoundException(id)).toDto()

    // Same transaction as the aggregate write: either both are stored or neither is.
    private fun record(aggregateId: UUID, event: Any, at: Instant) {
        outbox.save(OutboxEvent.record(aggregateId, event, objectMapper.writeValueAsString(event), at))
    }

    // PostgreSQL timestamptz stores microseconds; truncate so values round-trip unchanged.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun Subscription.toDto() =
        SubscriptionDto(id, customerId, plan, status.name, startedAt, cancelledAt)
}
