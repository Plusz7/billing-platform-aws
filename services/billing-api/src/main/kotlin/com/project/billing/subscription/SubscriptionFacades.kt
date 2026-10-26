package com.project.billing.subscription

import java.util.UUID

interface SubscriptionCommandFacade {
    fun subscribe(customerId: String, plan: String): SubscriptionDto
    fun cancel(id: UUID): SubscriptionDto
}

interface SubscriptionQueryFacade {
    fun get(id: UUID): SubscriptionDto
}

class SubscriptionNotFoundException(id: UUID) : RuntimeException("Subscription $id not found")
