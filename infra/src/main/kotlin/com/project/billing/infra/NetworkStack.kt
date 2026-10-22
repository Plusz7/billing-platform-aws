package com.project.billing.infra

import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.ec2.GatewayVpcEndpointAwsService
import software.amazon.awscdk.services.ec2.GatewayVpcEndpointOptions
import software.amazon.awscdk.services.ec2.SubnetConfiguration
import software.amazon.awscdk.services.ec2.SubnetType
import software.amazon.awscdk.services.ec2.Vpc
import software.constructs.Construct

/** VPC with public and isolated subnets. No NAT Gateway on purpose (cost rule); the S3 gateway endpoint is free. */
class NetworkStack(scope: Construct, id: String, props: StackProps) : Stack(scope, id, props) {

    val vpc: Vpc = Vpc.Builder.create(this, "Vpc")
        .maxAzs(2)
        .natGateways(0)
        .subnetConfiguration(
            listOf(
                SubnetConfiguration.builder().name("public").subnetType(SubnetType.PUBLIC).cidrMask(24).build(),
                SubnetConfiguration.builder().name("private").subnetType(SubnetType.PRIVATE_ISOLATED).cidrMask(24).build(),
            ),
        )
        .gatewayEndpoints(
            mapOf("S3" to GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.S3).build()),
        )
        .build()
}
