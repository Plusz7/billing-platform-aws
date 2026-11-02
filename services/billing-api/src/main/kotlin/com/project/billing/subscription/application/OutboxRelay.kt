package com.project.billing.subscription.application

import com.project.billing.subscription.domain.EventPublisher
import com.project.billing.subscription.domain.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Publishes stored outbox events. Delivery is at-least-once: if the process dies after publishing but before the
 * commit, the event is published again, so consumers must be idempotent. Does nothing when no broker is configured.
 */
@Service
internal class OutboxRelay(
    private val outbox: OutboxEventRepository,
    private val publisher: ObjectProvider<EventPublisher>,
    private val clock: Clock,
    @param:Value("\${billing.outbox.batch-size}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${billing.outbox.poll-interval}",
        fixedDelayString = "\${billing.outbox.poll-interval}",
    )
    @Transactional
    fun publishPending() {
        val eventPublisher = publisher.ifAvailable ?: return
        for (event in outbox.lockUnpublished(batchSize)) {
            try {
                eventPublisher.publish(event)
            } catch (e: Exception) {
                // Keep the order: stop at the first failure and retry from it on the next run.
                log.warn("Publishing outbox event {} failed, will retry", event.id, e)
                return
            }
            event.markPublished(Instant.now(clock).truncatedTo(ChronoUnit.MICROS))
            outbox.save(event)
        }
    }
}
