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

    val props = StackProps.builder().env(env).build()
    val network = NetworkStack(app, "NetworkStack", props)
    val data = DataStack(app, "DataStack", props, network.vpc)
    val messaging = MessagingStack(app, "MessagingStack", props)
    val imageTag = app.node.tryGetContext("imageTag") as String? ?: "latest"
    // 0 on the first deploy: the ECR repository must exist and hold the image before a task can start.
    val desiredCount = (app.node.tryGetContext("desiredCount") as String?)?.toInt() ?: 1
    ComputeStack(app, "ComputeStack", props, network.vpc, data.database, messaging.topic, messaging.queue, imageTag, desiredCount)

    app.synth()
}
