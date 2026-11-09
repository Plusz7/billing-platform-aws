# ADR 0002: Outbox + SNS fan-out to SQS queues

Status: accepted

## Context
Creating a subscription must trigger invoice generation, but a failed or slow consumer must not break or slow
the API, and an event must never be lost or sent for a rolled-back transaction.

## Decision
- Write the aggregate and an outbox row in one DB transaction; a scheduled job publishes to SNS.
- SNS topic fans out to one SQS queue per consumer, each with its own DLQ.
- The invoice Lambda consumes its queue through an event source mapping.

## Consequences
- Delivery is at-least-once, so consumers must be idempotent (the Lambda is: same message, one invoice).
- A poison message ends up in a DLQ after the retry limit instead of blocking the queue; DLQ alarms notify.
- Adding a consumer means a new queue subscribed to the topic, with no change to the publisher.
- Rejected: publishing to SNS inside the transaction (dual-write problem); direct HTTP calls (tight coupling);
  Kinesis/EventBridge (more than this volume needs).
