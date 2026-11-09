package com.project.billing.infra

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.cloudwatch.Alarm
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator
import software.amazon.awscdk.services.cloudwatch.Dashboard
import software.amazon.awscdk.services.cloudwatch.GraphWidget
import software.amazon.awscdk.services.cloudwatch.IMetric
import software.amazon.awscdk.services.cloudwatch.MetricOptions
import software.amazon.awscdk.services.cloudwatch.TreatMissingData
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction
import software.amazon.awscdk.services.elasticloadbalancingv2.HttpCodeTarget
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.rds.DatabaseInstance
import software.amazon.awscdk.services.sns.Topic
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription
import software.amazon.awscdk.services.sqs.Queue
import software.constructs.Construct

/**
 * CloudWatch alarms (all notify one SNS topic) and a dashboard. Deployed last, destroyed first: it only reads
 * metrics of the other stacks and nothing depends on it.
 */
class ObservabilityStack(
    scope: Construct,
    id: String,
    props: StackProps,
    compute: ComputeStack,
    database: DatabaseInstance,
    invoicingDlq: Queue,
    invoiceGenerationDlq: Queue,
    invoiceFunction: Function,
    alarmEmail: String?,
) : Stack(scope, id, props) {

    private val alarmTopic: Topic = Topic.Builder.create(this, "AlarmTopic").topicName("billing-alarms").build()
    private val alarmAction = SnsAction(alarmTopic)

    init {
        // Email subscription must be confirmed by clicking the link AWS sends to the address.
        alarmEmail?.let { alarmTopic.addSubscription(EmailSubscription(it)) }

        val oneMinute = Duration.minutes(1)
        val fiveMinutes = Duration.minutes(5)

        val alb5xx = compute.targetGroup.metrics.httpCodeTarget(
            HttpCodeTarget.TARGET_5XX_COUNT,
            MetricOptions.builder().period(oneMinute).statistic("Sum").build(),
        )
        val latencyP95 = compute.targetGroup.metrics.targetResponseTime(
            MetricOptions.builder().period(fiveMinutes).statistic("p95").build(),
        )
        val unhealthyHosts = compute.targetGroup.metrics.unhealthyHostCount(
            MetricOptions.builder().period(oneMinute).statistic("Maximum").build(),
        )
        val serviceCpu = compute.service.metricCpuUtilization(
            MetricOptions.builder().period(fiveMinutes).build(),
        )
        val dbCpu = database.metricCPUUtilization(MetricOptions.builder().period(fiveMinutes).build())
        val dbFreeStorage = database.metricFreeStorageSpace(
            MetricOptions.builder().period(fiveMinutes).statistic("Minimum").build(),
        )
        val invoicingDlqDepth = invoicingDlq.metricApproximateNumberOfMessagesVisible(
            MetricOptions.builder().period(oneMinute).statistic("Maximum").build(),
        )
        val invoiceDlqDepth = invoiceGenerationDlq.metricApproximateNumberOfMessagesVisible(
            MetricOptions.builder().period(oneMinute).statistic("Maximum").build(),
        )
        val lambdaErrors = invoiceFunction.metricErrors(
            MetricOptions.builder().period(fiveMinutes).statistic("Sum").build(),
        )
        val lambdaThrottles = invoiceFunction.metricThrottles(
            MetricOptions.builder().period(fiveMinutes).statistic("Sum").build(),
        )

        alarm("Alb5xx", "ALB targets returned 5xx", alb5xx, 5.0, 2)
        alarm("LatencyP95", "p95 response time above 1 s", latencyP95, 1.0, 3)
        alarm("UnhealthyHosts", "ALB sees an unhealthy task", unhealthyHosts, 1.0, 2)
        alarm("ServiceCpu", "Fargate CPU above 85% (autoscaling at max?)", serviceCpu, 85.0, 3)
        alarm("DatabaseCpu", "RDS CPU above 80%", dbCpu, 80.0, 3)
        alarm(
            "DatabaseFreeStorage", "RDS free storage below 2 GiB", dbFreeStorage, 2.0 * 1024 * 1024 * 1024, 1,
            ComparisonOperator.LESS_THAN_THRESHOLD,
        )
        alarm("InvoicingDlq", "Messages in invoicing-events-dlq", invoicingDlqDepth, 1.0, 1)
        alarm("InvoiceGenerationDlq", "Messages in invoice-generation-dlq", invoiceDlqDepth, 1.0, 1)
        alarm("LambdaErrors", "generate-invoice Lambda errors", lambdaErrors, 1.0, 1)
        alarm("LambdaThrottles", "generate-invoice Lambda throttled", lambdaThrottles, 1.0, 1)

        val dashboard = Dashboard.Builder.create(this, "Dashboard").dashboardName("billing-platform").build()
        dashboard.addWidgets(
            graph("ALB 5xx", alb5xx),
            graph("Latency p95 (s)", latencyP95),
            graph("Fargate CPU % / tasks", serviceCpu),
        )
        dashboard.addWidgets(
            graph("RDS CPU %", dbCpu),
            graph("DLQ depth", invoicingDlqDepth, invoiceDlqDepth),
            graph("Lambda errors / throttles", lambdaErrors, lambdaThrottles),
        )

        CfnOutput.Builder.create(this, "AlarmTopicArn").value(alarmTopic.topicArn).build()
    }

    private fun alarm(
        id: String,
        description: String,
        metric: IMetric,
        threshold: Double,
        evaluationPeriods: Int,
        operator: ComparisonOperator = ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    ) {
        Alarm.Builder.create(this, "${id}Alarm")
            .alarmName("billing-$id")
            .alarmDescription(description)
            .metric(metric)
            .threshold(threshold)
            .evaluationPeriods(evaluationPeriods)
            .comparisonOperator(operator)
            // No traffic means no data points; that is not an incident.
            .treatMissingData(TreatMissingData.NOT_BREACHING)
            .build()
            .addAlarmAction(alarmAction)
    }

    private fun graph(title: String, vararg metrics: IMetric): GraphWidget =
        GraphWidget.Builder.create().title(title).left(metrics.toList()).width(8).build()
}
