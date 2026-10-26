package com.project.billing.subscription

import com.project.billing.subscription.domain.Subscription
import com.project.billing.subscription.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SubscriptionTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun `cancel marks subscription cancelled`() {
        val subscription = Subscription.start("cust-1", "BASIC", now)
        subscription.cancel(now.plusSeconds(60))
        assertEquals(SubscriptionStatus.CANCELLED, subscription.status)
        assertNotNull(subscription.cancelledAt)
    }

    @Test
    fun `cancelling twice fails`() {
        val subscription = Subscription.start("cust-1", "BASIC", now)
        subscription.cancel(now)
        assertThrows<IllegalStateException> { subscription.cancel(now) }
    }
}
