# Getting Started

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (Temurin) | 26 | `sdk install java 26-tem` via SDKMAN, or download from adoptium.net |
| Docker + Docker Compose | any recent | required for local Postgres + MinIO |
| Maven | bundled | use `./mvnw` — do not install separately |

---

## 1. Clone and configure

```bash
git clone <repo-url>
cd garden
cp .env.example .env
```

The `.env` file is read automatically when running with the `local` Spring profile (the default for local dev). The keys in `.env.example` are commented where optional. For basic local development you only need to set:

- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — only if you need Google OAuth2 login
- Everything else works with the defaults (local dev JWT keys, MinIO, no real mail)

---

## 2. Start infrastructure

```bash
docker compose up -d
```

This starts:
- **Postgres 17** on `localhost:5432` — database `garden`, user/password `garden`
- **MinIO** on `localhost:9000` (API) and `localhost:9001` (console) — credentials `minioadmin/minioadmin`
- **minio-init** — one-off container that creates the `garden` and `garden-private` buckets

Wait for healthy status before starting the app:

```bash
docker compose ps
```

---

## 3. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on `http://localhost:8080`.

Flyway runs all migrations automatically on startup. The schema is created fresh on the first run. Subsequent starts apply only new migrations.

---

## 4. Seed a superuser

On first startup, a superuser is created automatically via `SuperUserCommand`:

| Field | Default value |
|---|---|
| Email | `owner@garden.local` |
| Password | `changeme` |

Override via `.env`:
```
SUPERUSER_EMAIL=you@example.com
SUPERUSER_PASSWORD=yourpassword
```

The superuser has the `OWNER` role and all permissions. Use it to log in to the admin API.

---

## 5. Verify it's running

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@garden.local","password":"changeme"}'
# → {"accessToken":"...","refreshToken":"..."}
```

---

## 6. OpenAPI / Swagger UI

Swagger UI is available in local and demo profiles:

```
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Swagger is **disabled in production** (`application-prod.properties` sets `springdoc.api-docs.enabled=false`).

---

## Development Workflow

### Making schema changes

Add a new Flyway migration file:

```
src/main/resources/db/migration/V{N+1}__{short_description}.sql
```

Rules:
- Never edit an existing migration — Flyway checksums them and will reject changes
- Version numbers must be strictly increasing
- Snake-case description after `__`

### Running the app without Docker

If you have a local Postgres instance, override the datasource in `.env`:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/yourdb
SPRING_DATASOURCE_USERNAME=youruser
SPRING_DATASOURCE_PASSWORD=yourpassword
```

For storage, set `STORAGE_ENDPOINT` to point at any S3-compatible service.

### Hot reload

Spring DevTools is on the classpath and enabled in local profile. Save a file → the app reloads automatically (JVM context restart, not full restart — fast).

### Useful endpoints

| URL | Purpose |
|---|---|
| `GET /actuator/health` | Health check |
| `GET /actuator/prometheus` | Prometheus metrics scrape |
| `GET /swagger-ui.html` | Interactive API docs (local/demo only) |
| `localhost:9001` | MinIO console — browse uploaded files |
