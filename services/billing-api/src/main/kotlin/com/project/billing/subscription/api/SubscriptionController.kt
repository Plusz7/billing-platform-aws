package com.project.billing.subscription.api

import com.project.billing.subscription.SubscriptionCommandFacade
import com.project.billing.subscription.SubscriptionDto
import com.project.billing.subscription.SubscriptionNotFoundException
import com.project.billing.subscription.SubscriptionQueryFacade
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

internal data class SubscribeRequest(val customerId: String, val plan: String)

@RestController
@RequestMapping("/subscriptions")
internal class SubscriptionController(
    private val commands: SubscriptionCommandFacade,
    private val queries: SubscriptionQueryFacade,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(@RequestBody request: SubscribeRequest): SubscriptionDto =
        commands.subscribe(request.customerId, request.plan)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): SubscriptionDto = queries.get(id)

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: UUID): SubscriptionDto = commands.cancel(id)

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun notFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun invalid(e: RuntimeException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.message)
}
