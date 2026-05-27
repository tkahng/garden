# Deployment

## Overview

The app is deployed as a Docker container image pushed to GitHub Container Registry (`ghcr.io`) and deployed to a Dokploy instance. The pipeline is fully automated via GitHub Actions on every push to `main`.

---

## CI/CD Pipeline

### CI (`.github/workflows/ci.yml`) — runs on every PR to `main`

```
pull_request → main
  ├── Unit Tests         (./mvnw test -Dtest="**/*Test,**/*Tests")
  ├── Integration Tests  (./mvnw test -Dtest="**/*IT,!DevDataSeederIT")
  │   └── (runs after unit tests pass)
  └── Seeder Tests       (./mvnw test -Dtest="DevDataSeederIT")
      └── (runs after unit tests pass, parallel with integration)
```

All three jobs must pass before a PR can be merged.

### Deploy (`.github/workflows/deploy.yml`) — runs on every push to `main`

```
push → main
  ├── Build & Push Image
  │   ├── docker buildx build --platform linux/amd64
  │   ├── push → ghcr.io/{owner}/{repo}:latest
  │   └── (uses GitHub Actions cache for layer reuse)
  └── Deploy to Dokploy
      └── POST Dokploy API → triggers pull + restart of the container
```

Required GitHub secrets:
- `DOKPLOY_HOST` — Dokploy instance URL
- `DOKPLOY_AUTH_TOKEN` — API token
- `DOKPLOY_APPLICATION_ID` — app identifier in Dokploy

---

## Docker

### Build image locally

```bash
docker build -t garden:local .
```

### Run image locally (requires external Postgres + MinIO)

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/garden \
  -e DB_USERNAME=garden \
  -e DB_PASSWORD=garden \
  -e JWT_PRIVATE_KEY=... \
  -e JWT_PUBLIC_KEY=... \
  -e STRIPE_SECRET_KEY=sk_... \
  -e STRIPE_WEBHOOK_SECRET=whsec_... \
  -e STORAGE_ENDPOINT=... \
  -e STORAGE_BUCKET=garden \
  -e STORAGE_PRIVATE_BUCKET=garden-private \
  -e STORAGE_ACCESS_KEY=... \
  -e STORAGE_SECRET_KEY=... \
  -e STORAGE_BASE_URL=... \
  garden:local
```

### Full local stack (app + Postgres + MinIO)

```bash
docker compose up
```

The `docker-compose.yaml` in the project root is for local development only. It sets `SPRING_PROFILES_ACTIVE=local` and uses hardcoded dev credentials.

---

## Environment Variables Reference

### Required in production

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` |
| `DB_URL` | JDBC URL — `jdbc:postgresql://host:5432/dbname` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_PRIVATE_KEY` | Base64-encoded PKCS8 DER private key (RSA-2048) |
| `JWT_PUBLIC_KEY` | Base64-encoded X.509 DER public key |
| `STRIPE_SECRET_KEY` | Stripe secret key (`sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_...`) |
| `STORAGE_ENDPOINT` | S3-compatible endpoint URL (e.g. Cloudflare R2) |
| `STORAGE_BUCKET` | Public bucket name |
| `STORAGE_PRIVATE_BUCKET` | Private bucket name |
| `STORAGE_ACCESS_KEY` | S3 access key |
| `STORAGE_SECRET_KEY` | S3 secret key |
| `STORAGE_BASE_URL` | Public base URL for served files |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port (e.g. 587) |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password / app password |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `FRONTEND_URL` | Frontend HTTPS origin — used for OAuth2 redirect and CORS |

### Optional

| Variable | Default | Description |
|---|---|---|
| `SUPERUSER_EMAIL` | `owner@garden.local` | Superuser email seeded on first startup |
| `SUPERUSER_PASSWORD` | `changeme` | Superuser password — **change this in production** |
| `ADMIN_NOTIFICATION_EMAIL` | (empty) | Email to notify on new quote submissions |
| `STRIPE_AUTOMATIC_TAX_ENABLED` | `false` | Enable Stripe Tax (requires activation on Stripe account) |
| `MAIL_SMTP_AUTH` | `true` (prod) | SMTP authentication |
| `MAIL_SMTP_STARTTLS` | `true` (prod) | SMTP STARTTLS |

### Generating RSA keys for production

```bash
# Private key (PKCS8 DER, base64)
openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n'

# Public key (X.509 DER, base64) — run from the same private key
# (pipe the private key into both commands, or save to a temp file)
openssl genrsa 2048 > /tmp/key.pem
openssl pkcs8 -topk8 -nocrypt -outform DER -in /tmp/key.pem | base64 | tr -d '\n'  # → JWT_PRIVATE_KEY
openssl rsa -pubout -outform DER -in /tmp/key.pem | base64 | tr -d '\n'             # → JWT_PUBLIC_KEY
rm /tmp/key.pem
```

The default keys bundled in `application.properties` are **development-only** and must not be used in production.

---

## Health Check

```bash
curl https://your-domain.com/actuator/health
# → {"status":"UP"}
```

Prometheus metrics (if scraping is configured):
```
GET /actuator/prometheus
```

---

## Profiles

| Profile | When used | Key differences |
|---|---|---|
| `local` | Local development | Dev JWT keys, MinIO, no-op mail, Swagger UI enabled |
| `demo` | Demo/staging | Production-like config, Swagger UI enabled |
| `prod` | Production | Swagger disabled, all credentials via env vars |
| `test` | CI / integration tests | Testcontainers Postgres, no real external services |

Activate via `SPRING_PROFILES_ACTIVE=prod` or `-Dspring-boot.run.profiles=local`.

---

## Deployment Platform

The app is deployed via **Dokploy** (self-hosted PaaS). Dokploy pulls the Docker image from GHCR and manages container lifecycle, environment variables, and routing.

The deploy workflow calls the Dokploy API to trigger a redeploy after each successful image push. Zero-downtime deploys depend on Dokploy's rolling restart configuration.

---

## Storage: Cloudflare R2

Production uses Cloudflare R2 (S3-compatible). Two buckets:
- `STORAGE_BUCKET` (e.g. `garden`) — public; files served via `STORAGE_BASE_URL`
- `STORAGE_PRIVATE_BUCKET` (e.g. `garden-private`) — not publicly accessible; presigned URLs used for access

The `S3StorageService` implementation uses AWS SDK v2 with a custom `S3Configuration` that overrides the endpoint URL, making it compatible with any S3-compatible service.
