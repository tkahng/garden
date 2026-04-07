# GitHub Actions CI — Design Spec

**Date:** 2026-04-07

## Overview

Add a GitHub Actions workflow that runs tests on every pull request targeting `main`. The workflow splits unit and integration tests into two sequential jobs so unit tests provide fast feedback and integration tests only run when unit tests pass.

## Trigger

```yaml
on:
  pull_request:
    branches: [main]
```

## Jobs

### Job 1: `test-unit`

- **Runner:** `ubuntu-latest`
- **Java:** 25 (Temurin distribution via `actions/setup-java`)
- **Cache:** `~/.m2/repository` keyed on `hashFiles('**/pom.xml')`
- **Command:** `./mvnw test -Dtest="**/*Test,**/*Tests" -DfailIfNoTests=false`

Runs only unit tests (files matching `*Test.java` / `*Tests.java`). No Docker required.

### Job 2: `test-integration`

- **Runner:** `ubuntu-latest`
- **Dependency:** `needs: test-unit` — skipped if unit tests fail
- **Java:** 25 (same setup and cache as job 1)
- **Command:** `./mvnw test -Dtest="**/*IT" -DfailIfNoTests=false`

Runs only integration tests (files matching `*IT.java`). Testcontainers uses the Docker socket available on GitHub-hosted runners — no additional Docker setup needed. PostgreSQL and MinIO containers are started by Testcontainers at test runtime.

## Maven Cache Strategy

Cache key: `${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}`
Cache path: `~/.m2/repository`

Cache restores on hit and saves on miss. Invalidates automatically when `pom.xml` changes.

## File Layout

```
.github/
  workflows/
    ci.yml
```

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Single vs. multiple workflow files | Single file | Simpler; `needs:` enforces ordering natively |
| Job structure | Two sequential jobs | Unit tests fail fast; integration tests skipped if unit fails |
| Docker setup for Testcontainers | None needed | `ubuntu-latest` runners expose Docker socket by default |
| Test separation mechanism | `-Dtest=` pattern flag | Matches existing Surefire `<includes>` config in `pom.xml` |
