package com.project.billing.infra

import software.amazon.awscdk.Duration
import software.amazon.awscdk.RemovalPolicy
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.ec2.InstanceClass
import software.amazon.awscdk.services.ec2.InstanceSize
import software.amazon.awscdk.services.ec2.InstanceType
import software.amazon.awscdk.services.ec2.SubnetSelection
import software.amazon.awscdk.services.ec2.SubnetType
import software.amazon.awscdk.services.ec2.Vpc
import software.amazon.awscdk.services.rds.Credentials
import software.amazon.awscdk.services.rds.DatabaseInstance
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine
import software.amazon.awscdk.services.rds.PostgresEngineVersion
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps
import software.constructs.Construct

/** Single-AZ PostgreSQL in the isolated subnets. The master password is generated into Secrets Manager. */
class DataStack(scope: Construct, id: String, props: StackProps, vpc: Vpc) : Stack(scope, id, props) {

    val database: DatabaseInstance = DatabaseInstance.Builder.create(this, "Database")
        .engine(
            DatabaseInstanceEngine.postgres(
                PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_16).build(),
            ),
        )
        .instanceType(InstanceType.of(InstanceClass.BURSTABLE4_GRAVITON, InstanceSize.MICRO))
        .vpc(vpc)
        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_ISOLATED).build())
        .credentials(Credentials.fromGeneratedSecret("billing"))
        .databaseName("billing")
        .allocatedStorage(20)
        .multiAz(false)
        .publiclyAccessible(false)
        .backupRetention(Duration.days(0))
        .deletionProtection(false)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build()
}
