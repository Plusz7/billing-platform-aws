# ADR 0005: Cost-minimal topology for a portfolio project

Status: accepted

## Decision
- No NAT Gateway; Fargate tasks sit in public subnets, RDS in isolated subnets.
- Single-AZ `t4g.micro` RDS, no Multi-AZ.
- Fargate 0.5 vCPU / 1 GB, one task by default.
- DynamoDB on-demand, 1-week log retention, all stacks disposable (`cdk destroy`).

## Consequences
- Not production-grade availability: an AZ or RDS failure means downtime.
- For production: Multi-AZ RDS, tasks in private subnets with VPC endpoints or NAT, HTTPS on the ALB, WAF.
- Known gaps from load testing: autoscaling did not scale out at about 78% CPU in a 10-minute test (the 60%
  target needs several sustained datapoints), and X-Ray for the Fargate service is not implemented (needs
  app instrumentation and an ADOT sidecar).
