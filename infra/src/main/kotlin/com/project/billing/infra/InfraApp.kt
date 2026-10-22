package com.project.billing.infra

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps

fun main() {
    val app = App()
    val env = Environment.builder()
        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
        .region(System.getenv("CDK_DEFAULT_REGION"))
        .build()

    NetworkStack(app, "NetworkStack", StackProps.builder().env(env).build())

    app.synth()
}
