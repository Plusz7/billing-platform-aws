# ADR 0001: ECS Fargate for billing-api

Status: accepted

## Context
billing-api is a long-running Spring Boot service with a JVM start-up of several seconds, a scheduled outbox job
and an SQS consumer loop.

## Decision
Run it on ECS Fargate behind an ALB.

## Consequences
- No servers to patch; scaling is a service setting (1-3 tasks on 60% CPU).
- Lambda was rejected for the API: cold starts, and the background outbox/consumer loops do not fit its model.
  Lambda is used where it fits, for the event-triggered invoice generation (ADR 0002).
- EC2 was rejected: more operations work for no benefit at this size.
- Fargate costs more per vCPU-hour than EC2, which is accepted for lower operational effort.
- Tasks run in public subnets with public IPs and a restrictive security group, because a NAT Gateway
  (about 30 USD/month idle) would cost more than the rest of the project (ADR 0005).
