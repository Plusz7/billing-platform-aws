package com.project.billing.subscription.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query(
        "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockUnpublished(@Param("limit") limit: Int): List<OutboxEventJpaEntity>
}
