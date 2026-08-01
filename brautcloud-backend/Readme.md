# Brautcloud backend

Spring Boot backend for Brautcloud. Runtime operations and launch gates live in
[`../RUNBOOK.md`](../RUNBOOK.md); storage-specific controls live in
[`STORAGE_OPERATIONS.md`](STORAGE_OPERATIONS.md).

## Local development

Compose provides local PostgreSQL and AiStor dependencies only. It is not a
production deployment or backup solution. Images are digest-pinned, ports bind to
loopback, and named volumes persist local data.

```bash
cd brautcloud-backend
cp .env.example .env
# Replace every CHANGE_ME value. Keep .env and .local/ untracked.
mkdir -p .local
# Provide .local/minio.license when using the licensed AiStor image.
docker compose up -d
```

Run Spring Boot separately with `.env` values exported or supplied by Doppler:

```bash
set -a; . ./.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`APP_COOKIE_SECURE=false` and an HTTP `AWS_ENDPOINT` are local-only exceptions.
Never copy those values into production. `docker compose down` preserves named
volumes; removing them intentionally discards local database and object data.

## Runtime configuration

Production values must come from the deployment target's secret/configuration
system. Never commit credentials or expose them to the frontend.

| Variable | Purpose |
|---|---|
| `POSTGRES_URL` | JDBC URL for PostgreSQL. |
| `POSTGRES_USER`, `POSTGRES_PW` | Database credentials. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Least-privilege S3-compatible storage credentials or workload identity. |
| `AWS_ENDPOINT` | S3-compatible endpoint; HTTPS outside local/test. |
| `JWT_SECRET` | Required high-entropy JWT signing secret; use at least 256 bits. |
| `APP_ALLOWED_ORIGINS` | Exact comma-separated trusted browser origins. |
| `APP_COOKIE_SECURE` | `true` in production; `false` only for local HTTP. |
| `APP_COOKIE_SAME_SITE` | Auth refresh-cookie SameSite policy, normally `Strict`. |
| `APP_GUEST_COOKIE_SAME_SITE` | Guest-cookie SameSite policy, normally `Lax`. |

JWT secret rotation is operationally significant: this version has one active
verification secret and no dual-key transition. Coordinate a backend restart,
expect existing JWT access/guest tokens to require re-authentication, verify
refresh behavior, and retire the old value through the secret manager. Do not
attempt zero-downtime rotation until overlapping-key support exists.

## Database migrations

Flyway owns `src/main/resources/db/migration`; Hibernate schema generation is
disabled. Run migrations through one controlled startup owner. Never edit an
applied migration or bypass Flyway. Back up and rehearse restore before destructive
or locking changes. Use expand/contract compatibility before removing old columns.
A failed migration must be fixed or restored by the database owner; do not switch
to H2 or enable automatic Hibernate DDL.

## Tests

Prerequisites for the complete suite:

- JDK 21 (the Maven wrapper downloads Maven itself).
- Docker-compatible container daemon with permission to run PostgreSQL 16.

Run from a clean build:

```bash
./mvnw clean test
```

The suite uses PostgreSQL Testcontainers for production-like SQL semantics and safe
storage test doubles. It does not need Doppler, local Compose, live AWS credentials,
or a live S3-compatible service. Focused tests can run without a container, for example:

```bash
./mvnw -Dtest=JwtServiceTest,EventControllerWebMvcTest test
```

For rootless Podman:

```bash
systemctl --user start podman.socket
DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock" \
  TESTCONTAINERS_RYUK_DISABLED=true ./mvnw clean test
```
