# ADR 0004: GitHub Actions authenticates to AWS with OIDC

Status: accepted

## Decision
The deploy workflow assumes an IAM role through GitHub's OIDC provider. No long-lived access keys are stored in
GitHub.

## Consequences
- Credentials are short-lived and only issued to this repository's `main` branch (trust policy on the `sub` claim).
- The repository uses immutable subject claims, so the prefix is passed with `-c githubSubjectPrefix`.
- The workflow is manual (`workflow_dispatch`) and deploys only the messaging and invoice stacks; the Fargate
  stacks are deployed by hand to avoid idle cost.
