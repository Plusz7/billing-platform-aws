package com.project.billing.invoicing.infrastructure.persistence

import com.project.billing.invoicing.domain.InvoiceStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invoice")
internal class InvoiceJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "subscription_id")
    var subscriptionId: UUID,
    @Column(name = "customer_id")
    var customerId: String,
    var plan: String,
    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus,
    @Column(name = "issued_at")
    var issuedAt: Instant,
)
