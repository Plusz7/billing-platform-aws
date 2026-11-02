package com.project.billing.subscription.infrastructure.persistence

import com.project.billing.subscription.domain.OutboxEvent
import com.project.billing.subscription.domain.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository
internal class OutboxEventRepositoryAdapter(
    private val jpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {

    override fun save(event: OutboxEvent): OutboxEvent =
        jpaRepository.save(event.toEntity()).toDomain()

    override fun lockUnpublished(limit: Int): List<OutboxEvent> =
        jpaRepository.lockUnpublished(limit).map { it.toDomain() }

    private fun OutboxEvent.toEntity() =
        OutboxEventJpaEntity(id, aggregateId, type, payload, createdAt, publishedAt)

    private fun OutboxEventJpaEntity.toDomain() =
        OutboxEvent(id, aggregateId, type, payload, createdAt, publishedAt)
}
