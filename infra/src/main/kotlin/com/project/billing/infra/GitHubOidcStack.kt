package com.project.billing.infra

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.iam.Effect
import software.amazon.awscdk.services.iam.OpenIdConnectProvider
import software.amazon.awscdk.services.iam.PolicyStatement
import software.amazon.awscdk.services.iam.Role
import software.amazon.awscdk.services.iam.WebIdentityPrincipal
import software.constructs.Construct

/**
 * Lets GitHub Actions deploy without long-lived keys: the workflow exchanges its OIDC token for short-lived
 * credentials of [deployRole]. The trust policy is the access control, so it pins audience and repository + branch.
 * Deployed once by hand (the pipeline cannot create its own entry door). The OIDC provider is one per account:
 * if the account already has one for token.actions.githubusercontent.com, import it instead of creating it.
 */
class GitHubOidcStack(scope: Construct, id: String, props: StackProps, githubRepo: String) : Stack(scope, id, props) {

    private val issuer = "token.actions.githubusercontent.com"

    val deployRole: Role

    init {
        val provider = OpenIdConnectProvider.Builder.create(this, "GitHubProvider")
            .url("https://$issuer")
            .clientIds(listOf("sts.amazonaws.com"))
            .build()

        deployRole = Role.Builder.create(this, "GitHubDeployRole")
            .roleName("github-actions-deploy")
            .maxSessionDuration(Duration.hours(1))
            .assumedBy(
                WebIdentityPrincipal(
                    provider.openIdConnectProviderArn,
                    mapOf(
                        "StringEquals" to mapOf(
                            "$issuer:aud" to "sts.amazonaws.com",
                            "$issuer:sub" to "repo:$githubRepo:ref:refs/heads/main",
                        ),
                    ),
                ),
            )
            .build()

        // CDK does the real work through the roles created by `cdk bootstrap`; this role only needs to assume them.
        deployRole.addToPolicy(
            PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(listOf("sts:AssumeRole"))
                .resources(listOf("arn:aws:iam::${this.account}:role/cdk-*"))
                .build(),
        )

        CfnOutput.Builder.create(this, "DeployRoleArn").value(deployRole.roleArn).build()
    }
}
