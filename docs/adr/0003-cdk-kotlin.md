# ADR 0003: AWS CDK in Kotlin, one stack per concern

Status: accepted

## Decision
Define infrastructure with AWS CDK in Kotlin, split into stacks by lifecycle: network, data, messaging,
compute, invoice, observability, OIDC.

## Consequences
- Same language and build tool (Gradle) as the services; constructs give sensible defaults and grant helpers
  (`grantPublish`, `grantConsumeMessages`) produce least-privilege IAM.
- Stacks can be destroyed independently; the expensive ones (compute, data) are torn down after each session
  while `GitHubOidcStack` stays.
- Deploy order matters on a fresh account: ComputeStack with `-c desiredCount=0`, push the image to ECR,
  then redeploy with `-c desiredCount=1`.
- Rejected: Terraform (extra language and state backend), raw CloudFormation (verbose).
