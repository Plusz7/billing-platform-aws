package com.project.billing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BillingApiApplication

fun main(args: Array<String>) {
    runApplication<BillingApiApplication>(*args)
}
