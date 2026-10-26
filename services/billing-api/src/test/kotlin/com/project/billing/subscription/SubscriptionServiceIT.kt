package com.project.billing.subscription

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@Testcontainers
class SubscriptionServiceIT {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var commands: SubscriptionCommandFacade

    @Autowired
    lateinit var queries: SubscriptionQueryFacade

    @Test
    fun `subscribe persists and can be read back`() {
        val created = commands.subscribe("cust-1", "BASIC")
        assertEquals(created, queries.get(created.id))
        assertEquals("ACTIVE", created.status)
    }

    @Test
    fun `cancel updates status`() {
        val created = commands.subscribe("cust-2", "PRO")
        assertEquals("CANCELLED", commands.cancel(created.id).status)
    }

    @Test
    fun `unknown subscription is not found`() {
        assertThrows<SubscriptionNotFoundException> { queries.get(UUID.randomUUID()) }
    }
}
