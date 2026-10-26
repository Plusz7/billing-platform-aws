package com.project.billing.subscription.infrastructure.persistence

import com.project.billing.subscription.domain.Subscription
import com.project.billing.subscription.domain.SubscriptionRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class SubscriptionRepositoryAdapter(
    private val jpaRepository: SubscriptionJpaRepository,
) : SubscriptionRepository {

    override fun save(subscription: Subscription): Subscription =
        jpaRepository.save(subscription.toEntity()).toDomain()

    override fun findById(id: UUID): Subscription? =
        jpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    private fun Subscription.toEntity() =
        SubscriptionJpaEntity(id, customerId, plan, status, startedAt, cancelledAt)

    private fun SubscriptionJpaEntity.toDomain() =
        Subscription(id, customerId, plan, status, startedAt, cancelledAt)
}
