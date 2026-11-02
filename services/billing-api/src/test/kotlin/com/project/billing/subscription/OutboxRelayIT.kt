package com.project.billing.subscription

import com.project.billing.subscription.application.OutboxRelay
import com.project.billing.subscription.domain.EventPublisher
import com.project.billing.subscription.domain.OutboxEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["billing.outbox.poll-interval=PT1H"])
@Testcontainers
@Import(OutboxRelayIT.RecordingPublisherConfig::class)
internal class OutboxRelayIT {

    class RecordingPublisher : EventPublisher {
        val published: MutableList<OutboxEvent> = Collections.synchronizedList(mutableListOf())
        @Volatile
        var failing = false

        override fun publish(event: OutboxEvent) {
            check(!failing) { "broker unavailable" }
            published += event
        }
    }

    @TestConfiguration
    class RecordingPublisherConfig {
        @Bean
        fun recordingPublisher() = RecordingPublisher()
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var commands: SubscriptionCommandFacade

    @Autowired
    lateinit var relay: OutboxRelay

    @Autowired
    lateinit var publisher: RecordingPublisher

    @BeforeEach
    fun reset() {
        publisher.failing = false
        relay.publishPending() // drain events left by other tests
        publisher.published.clear()
    }

    @Test
    fun `subscribe and cancel publish one event each, exactly once`() {
        val created = commands.subscribe("cust-outbox-1", "BASIC")
        commands.cancel(created.id)

        relay.publishPending()
        relay.publishPending()

        val events = publisher.published.filter { it.aggregateId == created.id }
        assertEquals(listOf("SubscriptionStartedEvent", "SubscriptionCancelledEvent"), events.map { it.type })
        assertTrue(events.first().payload.contains(created.id.toString()))
    }

    @Test
    fun `event stays in the outbox while the broker is down and is published after recovery`() {
        publisher.failing = true
        val created = commands.subscribe("cust-outbox-2", "PRO")

        relay.publishPending()
        assertTrue(publisher.published.none { it.aggregateId == created.id })

        publisher.failing = false
        relay.publishPending()
        assertEquals(1, publisher.published.count { it.aggregateId == created.id })
    }
}
