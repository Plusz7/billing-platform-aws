package com.project.billing.subscription.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SubscriptionJpaRepository : JpaRepository<SubscriptionJpaEntity, UUID>
