package com.project.billing.subscription.infrastructure.persistence

import com.project.billing.subscription.domain.SubscriptionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscription")
internal class SubscriptionJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "customer_id")
    var customerId: String,
    var plan: String,
    @Enumerated(EnumType.STRING)
    var status: SubscriptionStatus,
    @Column(name = "started_at")
    var startedAt: Instant,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant?,
)
