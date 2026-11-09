# Architecture

```mermaid
flowchart LR
    client([Client / k6]) -->|HTTP| alb[ALB]
    subgraph vpc [VPC - eu-central-1, 2 AZs, no NAT]
        alb --> api["billing-api<br/>ECS Fargate, 1-3 tasks"]
        api -->|JDBC| rds[(RDS PostgreSQL)]
    end
    api -->|publish, after outbox commit| sns{{SNS events topic}}
    sns --> q1[[SQS invoicing queue]]
    sns --> q2[[SQS invoice-generation queue]]
    q1 -->|consume| api
    q2 --> lambda[Lambda generate-invoice]
    lambda --> s3[(S3 invoices)]
    lambda --> ddb[(DynamoDB invoice index)]
    q1 -. failures .-> dlq1[[DLQ]]
    q2 -. failures .-> dlq2[[DLQ]]
    api & lambda & alb & rds --> cw[CloudWatch dashboard + alarms]
    cw --> alarms{{SNS billing-alarms}} --> mail([email])
    gh([GitHub Actions]) -->|OIDC, no stored keys| role[IAM role] -->|cdk deploy| cfn[CloudFormation]
```

## Stacks

| Stack | Contents |
|---|---|
| `NetworkStack` | VPC, public + isolated subnets, S3 gateway endpoint, no NAT Gateway |
| `DataStack` | RDS PostgreSQL (`t4g.micro`, single AZ, 20 GB), credentials in Secrets Manager |
| `MessagingStack` | SNS topic, SQS queues, DLQs (14 days retention) |
| `ComputeStack` | ECR, ECS cluster, Fargate service (0.5 vCPU / 1 GB), ALB, CPU target-tracking 1-3 tasks |
| `InvoiceStack` | Lambda (Java 21, 512 MB), S3 bucket, DynamoDB (on-demand) |
| `ObservabilityStack` | dashboard, alarms, `billing-alarms` SNS topic |
| `GitHubOidcStack` | OIDC provider + deploy role for GitHub Actions (optional, kept between sessions) |

## Request and event flow

1. `POST /subscriptions` saves the subscription and an outbox row in one DB transaction.
2. A scheduled outbox job publishes the event to SNS (never inside the business transaction).
3. SNS fans out to two SQS queues: one consumed by billing-api, one by the invoice Lambda.
4. The Lambda writes the invoice to S3 and records it in DynamoDB; duplicates are idempotent
   (two identical messages produce one invoice).
5. Messages that keep failing land in a DLQ, which has an alarm.

Decisions: see [adr/](adr/).
