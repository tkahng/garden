# ADR-0004: Flyway for Schema Evolution

**Status:** Accepted

## Context

Garden's schema spans roughly 60 tables across multiple logical domains (checkout, catalog, b2b, inventory, content). Schema changes need to be: reproducible across local, CI, and production environments; tracked in version control alongside the code that depends on them; and safe to apply without manual DBA intervention.

Options considered: Flyway, Liquibase, Hibernate `ddl-auto=update`, and manual SQL scripts.

## Decision

Use Flyway with versioned SQL migration scripts (`V{n}__{description}.sql`). Hibernate DDL auto-generation is disabled (`ddl-auto=validate`), so Flyway is the single source of truth for schema state. Each migration is a plain SQL file committed alongside the feature branch that requires it.

Testcontainers spins up a real PostgreSQL instance for integration tests, and Flyway runs the full migration chain before each test suite. This ensures migration correctness is continuously verified.

## Consequences

**Positive:**
- Schema history is auditable in git: every change has an author, a PR, and a description.
- `ddl-auto=validate` fails fast on startup if the schema drifts from the entity model, catching mismatches before they hit production.
- Integration tests run against the exact schema production will see.

**Negative:**
- Migrations are irreversible by default (Flyway Community Edition does not support undo scripts). Rollback requires a new forward migration.
- Renaming a column requires a multi-step migration (add new column, backfill, remove old column) rather than a single `ALTER TABLE ... RENAME`.
- With 75+ migrations, cold-start time on a fresh database adds ~2-3 seconds at startup.
