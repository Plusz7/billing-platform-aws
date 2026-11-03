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
    // Pass -c githubRepo=OWNER/REPO to create the OIDC role for GitHub Actions; add -c githubSubjectPrefix=... when
    // the repo uses immutable subject claims.
    (app.node.tryGetContext("githubRepo") as String?)?.let { repo ->
        val prefix = app.node.tryGetContext("githubSubjectPrefix") as String? ?: "repo:$repo"
        GitHubOidcStack(app, "GitHubOidcStack", props, repo, prefix)
    }
    val network =NetworkStack(app, "NetworkStack", props)
    val data = DataStack(app, "DataStack", props, network.vpc)
    val messaging = MessagingStack(app, "MessagingStack", props)
    val imageTag = app.node.tryGetContext("imageTag") as String? ?: "latest"
    // 0 on the first deploy: the ECR repository must exist and hold the image before a task can start.
    val desiredCount = (app.node.tryGetContext("desiredCount") as String?)?.toInt() ?: 1
    InvoiceStack(
        app, "InvoiceStack", props, messaging.invoiceGenerationQueue,
        "../services/invoice-lambda/build/distributions/invoice-lambda.zip",
    )
    ComputeStack(app, "ComputeStack", props, network.vpc, data.database, messaging.topic, messaging.queue, imageTag, desiredCount)

    app.synth()
}
