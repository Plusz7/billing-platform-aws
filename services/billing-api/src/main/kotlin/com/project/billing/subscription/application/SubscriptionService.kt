package com.project.billing.subscription.application

import com.project.billing.subscription.SubscriptionCommandFacade
import com.project.billing.subscription.SubscriptionDto
import com.project.billing.subscription.SubscriptionNotFoundException
import com.project.billing.subscription.SubscriptionQueryFacade
import com.project.billing.subscription.domain.Subscription
import com.project.billing.subscription.domain.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
internal class SubscriptionService(
    private val repository: SubscriptionRepository,
    private val clock: Clock,
) : SubscriptionCommandFacade, SubscriptionQueryFacade {

    @Transactional
    override fun subscribe(customerId: String, plan: String): SubscriptionDto =
        repository.save(Subscription.start(customerId, plan, now())).toDto()

    @Transactional
    override fun cancel(id: UUID): SubscriptionDto {
        val subscription = repository.findById(id) ?: throw SubscriptionNotFoundException(id)
        subscription.cancel(now())
        return repository.save(subscription).toDto()
    }

    @Transactional(readOnly = true)
    override fun get(id: UUID): SubscriptionDto =
        (repository.findById(id) ?: throw SubscriptionNotFoundException(id)).toDto()

    // PostgreSQL timestamptz stores microseconds; truncate so values round-trip unchanged.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun Subscription.toDto() =
        SubscriptionDto(id, customerId, plan, status.name, startedAt, cancelledAt)
}
