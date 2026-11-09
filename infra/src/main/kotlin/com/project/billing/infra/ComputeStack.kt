package com.project.billing.infra

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.Duration
import software.amazon.awscdk.RemovalPolicy
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress
import software.amazon.awscdk.services.ec2.SecurityGroup
import software.amazon.awscdk.services.ec2.SubnetSelection
import software.amazon.awscdk.services.ec2.SubnetType
import software.amazon.awscdk.services.ec2.Vpc
import software.amazon.awscdk.services.ecr.Repository
import software.amazon.awscdk.services.ecs.AwsLogDriverProps
import software.amazon.awscdk.services.ecs.Cluster
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions
import software.amazon.awscdk.services.ecs.ContainerImage
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps
import software.amazon.awscdk.services.ecs.FargateService
import software.amazon.awscdk.services.ecs.FargateTaskDefinition
import software.amazon.awscdk.services.ecs.LogDrivers
import software.amazon.awscdk.services.ecs.PortMapping
import software.amazon.awscdk.services.ecs.Secret
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck
import software.amazon.awscdk.services.logs.RetentionDays
import software.amazon.awscdk.services.rds.DatabaseInstance
import software.amazon.awscdk.services.sns.Topic
import software.amazon.awscdk.services.sqs.Queue
import software.constructs.Construct

/**
 * ECR repository, ALB and one Fargate task. The task runs in a public subnet with a public IP because there is no
 * NAT Gateway (cost rule); the ALB is the only intended entry point.
 */
class ComputeStack(
    scope: Construct,
    id: String,
    props: StackProps,
    vpc: Vpc,
    database: DatabaseInstance,
    eventsTopic: Topic,
    invoicingQueue: Queue,
    imageTag: String,
    desiredCount: Int,
): Stack(scope, id, props) {

    private val appPort = 8080

    val repository: Repository = Repository.Builder.create(this, "BillingApiRepository")
        .repositoryName("billing-api")
        .removalPolicy(RemovalPolicy.DESTROY)
        .emptyOnDelete(true)
        .build()

    // Exposed for ObservabilityStack (alarms and dashboard).
    val service: FargateService
    val alb: ApplicationLoadBalancer
    val targetGroup: ApplicationTargetGroup

    init {
        val cluster = Cluster.Builder.create(this, "Cluster").vpc(vpc).build()

        val taskDefinition = FargateTaskDefinition.Builder.create(this, "TaskDefinition")
            .cpu(512)
            .memoryLimitMiB(1024)
            .build()

        val dbSecret = requireNotNull(database.secret) { "DataStack must generate the database secret" }
        taskDefinition.addContainer(
            "billing-api",
            ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromEcrRepository(repository, imageTag))
                .portMappings(listOf(PortMapping.builder().containerPort(appPort).build()))
                .environment(
                    mapOf(
                        "DB_URL" to "jdbc:postgresql://${database.dbInstanceEndpointAddress}:" +
                            "${database.dbInstanceEndpointPort}/billing",
                        "EVENTS_TOPIC_ARN" to eventsTopic.topicArn,
                        "INVOICING_QUEUE_URL" to invoicingQueue.queueUrl,
                    ),
                )
                .secrets(
                    mapOf(
                        "DB_USERNAME" to Secret.fromSecretsManager(dbSecret, "username"),
                        "DB_PASSWORD" to Secret.fromSecretsManager(dbSecret, "password"),
                    ),
                )
                .logging(
                    LogDrivers.awsLogs(
                        AwsLogDriverProps.builder()
                            .streamPrefix("billing-api")
                            .logRetention(RetentionDays.ONE_WEEK)
                            .build(),
                    ),
                )
                .build(),
        )

        // Task role (the application's identity), not the execution role: the SDK in the code calls SNS.
        eventsTopic.grantPublish(taskDefinition.taskRole)
        invoicingQueue.grantConsumeMessages(taskDefinition.taskRole)

        val serviceSecurityGroup = SecurityGroup.Builder.create(this, "ServiceSecurityGroup").vpc(vpc).build()

        service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .taskDefinition(taskDefinition)
            .desiredCount(desiredCount)
            .assignPublicIp(true)
            .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
            .securityGroups(listOf(serviceSecurityGroup))
            .build()

        // Target tracking: ECS adds/removes tasks to keep the metric near the target. With desiredCount=0
        // (first deploy, before the image is pushed) the minimum stays 0 so autoscaling does not start a task.
        val scaling = service.autoScaleTaskCount(
            EnableScalingProps.builder()
                .minCapacity(if (desiredCount == 0) 0 else 1)
                .maxCapacity(3)
                .build(),
        )
        scaling.scaleOnCpuUtilization(
            "CpuScaling",
            CpuUtilizationScalingProps.builder()
                .targetUtilizationPercent(60)
                .scaleInCooldown(Duration.minutes(5))
                .scaleOutCooldown(Duration.minutes(1))
                .build(),
        )

        // Declared here rather than via database.connections so DataStack does not depend on ComputeStack.
        CfnSecurityGroupIngress.Builder.create(this, "DatabaseIngressFromService")
            .groupId(database.connections.securityGroups.first().securityGroupId)
            .sourceSecurityGroupId(serviceSecurityGroup.securityGroupId)
            .ipProtocol("tcp")
            .fromPort(5432)
            .toPort(5432)
            .build()

        alb = ApplicationLoadBalancer.Builder.create(this, "LoadBalancer").vpc(vpc).internetFacing(true).build()
        targetGroup = alb.addListener("Http", BaseApplicationListenerProps.builder().port(80).build())
            .addTargets(
                "BillingApi",
                AddApplicationTargetsProps.builder()
                    .port(appPort)
                    .protocol(ApplicationProtocol.HTTP)
                    .targets(listOf(service))
                    .healthCheck(HealthCheck.builder().path("/actuator/health/readiness").build())
                    .build(),
            )

        CfnOutput.Builder.create(this, "LoadBalancerDns").value(alb.loadBalancerDnsName).build()
    }
}
