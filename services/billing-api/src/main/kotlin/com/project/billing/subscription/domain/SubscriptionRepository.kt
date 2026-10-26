package com.project.billing.subscription.domain

import java.util.UUID

interface SubscriptionRepository {
    fun save(subscription: Subscription): Subscription
    fun findById(id: UUID): Subscription?
}
