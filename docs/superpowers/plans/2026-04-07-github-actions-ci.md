# GitHub Actions CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that runs unit tests and integration tests as two sequential jobs on every pull request targeting `main`.

**Architecture:** A single workflow file (`.github/workflows/ci.yml`) defines two jobs: `test-unit` runs `*Test`/`*Tests` classes via Maven Surefire without Docker; `test-integration` depends on `test-unit` via `needs:` and runs `*IT` classes which rely on Testcontainers (PostgreSQL + MinIO) using the Docker socket available on GitHub-hosted runners. Both jobs cache `~/.m2/repository` keyed on `pom.xml`.

**Tech Stack:** GitHub Actions, Java 25 (Temurin), Maven (`./mvnw`), Testcontainers, `actions/checkout@v4`, `actions/setup-java@v4`, `actions/cache@v4`

---

### Task 1: Create the GitHub Actions workflow file

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflows directory and file**

```bash
mkdir -p .github/workflows
```

- [ ] **Step 2: Write the workflow file**

Create `.github/workflows/ci.yml` with the following content:

```yaml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  test-unit:
    name: Unit Tests
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Cache Maven dependencies
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      - name: Run unit tests
        run: ./mvnw test -Dtest="**/*Test,**/*Tests" -DfailIfNoTests=false

  test-integration:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: test-unit

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Cache Maven dependencies
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      - name: Run integration tests
        run: ./mvnw test -Dtest="**/*IT" -DfailIfNoTests=false
```

- [ ] **Step 3: Verify the file is valid YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo "YAML valid"
```

Expected output: `YAML valid`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "feat: add GitHub Actions CI workflow for unit and integration tests"
```
