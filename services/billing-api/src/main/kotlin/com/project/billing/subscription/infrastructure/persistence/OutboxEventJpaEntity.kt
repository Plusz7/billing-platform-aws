package com.project.billing.subscription.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_event")
internal class OutboxEventJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "aggregate_id")
    var aggregateId: UUID,
    var type: String,
    var payload: String,
    @Column(name = "created_at")
    var createdAt: Instant,
    @Column(name = "published_at")
    var publishedAt: Instant?,
)
