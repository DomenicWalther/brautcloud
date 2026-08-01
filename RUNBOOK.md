# Launch and operations runbook

This runbook describes application prerequisites without selecting a production
provider, orchestrator, or hosting target. `brautcloud-backend/compose.yaml` is
for local dependencies only. It is not a production manifest.

## Runtime inventory

A deployment needs:

- Frontend static artifact, built with the approved public HTTPS API and app origins.
- Spring Boot backend on Java 21 or a compatible supported runtime.
- PostgreSQL database reachable through `POSTGRES_URL`.
- Private S3-compatible object storage reachable through `AWS_ENDPOINT`.
- TLS termination, DNS, and an edge/origin policy supplied by the deployment owner.

The repository contains no target-specific deployment manifest. The captain must
choose target, networking, process supervision, secret delivery, and scaling policy
before launch.

## Required configuration and secrets

Inject values through the approved secret/configuration system. Never commit them,
put them in frontend source, or use production values in Compose.

| Variable | Required behavior |
|---|---|
| `POSTGRES_URL` | JDBC PostgreSQL URL for application database. Use private network access. |
| `POSTGRES_USER` / `POSTGRES_PW` | Least-privilege application database credentials. Rotate through the target's secret store. |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Least-privilege bucket credentials or equivalent workload identity. Never expose to browser. |
| `AWS_ENDPOINT` | S3-compatible endpoint. Require HTTPS outside explicitly local/test execution. |
| `JWT_SECRET` | High-entropy signing secret, at least 256 bits, delivered only to backend instances. |
| `APP_ALLOWED_ORIGINS` | Exact comma-separated browser origins, including scheme; no wildcard. |
| `APP_COOKIE_SECURE` | `true` in production. Set `false` only for local HTTP development. |
| `APP_COOKIE_SAME_SITE` | Usually `Strict` for first-party auth cookies; choose deliberately if topology requires otherwise. |
| `APP_GUEST_COOKIE_SAME_SITE` | Usually `Lax`; verify guest flow against chosen frontend/API origins. |

### JWT secret rotation

`JWT_SECRET` is required and has no safe repository default. Current application
configuration uses one active signing secret and does not implement a dual-key
`kid` transition. A direct rotation invalidates outstanding JWT access/guest tokens
when all instances restart; refresh-session behavior must be verified separately.

Before rotation:

1. Confirm operators can update the secret atomically for every backend instance.
2. Prepare a new random 256-bit-or-stronger value in the secret manager; never generate it in Git or browser code.
3. Schedule a coordinated restart and expect brief re-authentication.
4. Verify login, refresh, guest access, and upload flows.
5. Record old-secret retirement and audit access to both values.

Do not perform zero-downtime rotation until application support for overlapping
verification keys is implemented and tested. Rotate immediately if exposure is
suspected, then revoke sessions as required by incident response.

## Cookie, origin, and proxy requirements

- Serve frontend and API over HTTPS in production.
- Set `APP_COOKIE_SECURE=true`; do not weaken it because a proxy terminates TLS.
- Set `APP_ALLOWED_ORIGINS` to exact trusted frontend origins. Keep local
  `http://localhost:4200` out of production values.
- Preserve `Origin` and forwarded headers through the selected TLS proxy; the app
  uses `server.forward-headers-strategy=framework`.
- Keep refresh and guest cookies HttpOnly as emitted by the backend. Do not move
  tokens into frontend localStorage.
- Verify browser preflight, login, refresh, logout, gallery access, and upload
  requests after any origin or cookie policy change.

## Database and migrations

- Provision PostgreSQL before starting the backend; do not substitute H2 for
  production-like validation.
- `spring.jpa.hibernate.ddl-auto=none`; Flyway owns schema changes under
  `brautcloud-backend/src/main/resources/db/migration`.
- Run migrations using one controlled startup/job owner. Do not run multiple
  independent migration processes unless target tooling guarantees locking.
- Back up PostgreSQL and verify restore before launch and before risky migrations.
- Never edit or reorder an applied migration. Add the next versioned migration.
- Review destructive or locking SQL, row counts, indexes, and backward compatibility.
- Deploy application code compatible with both old and new schema for expand/contract
  changes. Delay destructive cleanup until rollback window has closed.
- A failed migration blocks startup. Do not bypass Flyway or set Hibernate schema
  creation to recover. Restore/fix under the migration owner, then retry.

## Object storage

Apply [brautcloud-backend/STORAGE_OPERATIONS.md](brautcloud-backend/STORAGE_OPERATIONS.md)
for private bucket policy, least-privilege access, presigned URL behavior, lifecycle,
deletions, retries, versioning, and backup ownership. In particular:

- Keep bucket and object listing private; browser receives only presigned URLs.
- Configure incomplete-upload lifecycle expiry.
- Monitor storage deletion retries and reconcile database/bucket backups together.
- Validate endpoint TLS, path-style/signature behavior, CORS, and provider policy.

## Local-only Compose baseline

From `brautcloud-backend`:

```bash
cp .env.example .env
# Replace every CHANGE_ME value; keep .env untracked.
mkdir -p .local
# Place a locally licensed AiStor file at .local/minio.license if required.
docker compose up -d
```

Compose starts PostgreSQL and AiStor only. Ports bind to loopback, images are
pinned by digest, data uses named volumes, and healthchecks gate dependency
readiness. It does not run Spring Boot, configure TLS, provide backups, or model
production availability. Use `docker compose down` to stop services; do not remove
volumes unless intentionally discarding local data.

Run backend separately with variables from `.env` or Doppler and the `local` profile.
Tests use PostgreSQL Testcontainers and safe storage doubles; they do not need local
Compose services or production credentials.

## Launch gate

Before target-specific deployment, confirm:

- [ ] Approved target, owner, DNS, TLS, and runtime supervision exist outside this repo.
- [ ] CI and security checks pass on exact release commit.
- [ ] All required secrets/configuration are injected and audited.
- [ ] JWT rotation owner and incident procedure are recorded.
- [ ] PostgreSQL backup/restore and migration rehearsal passed.
- [ ] Private bucket policy, lifecycle, credentials, and restore ownership passed review.
- [ ] Production frontend artifact has approved HTTPS API/app origins, not localhost.
- [ ] Cookie/origin smoke tests pass through real proxy topology.
- [ ] Monitoring, alerts, log retention, and rollback owner are ready.

## Rollback and recovery

1. Stop promotion and preserve logs, migration output, and release identifier.
2. If schema migration ran, do not blindly roll back application binaries. Check
   compatibility and use a forward fix or restore procedure approved by the database owner.
3. Restore previous application artifact only when its schema compatibility is proven.
4. For destructive migration or data corruption, stop writes, use verified PostgreSQL
   and object-storage recovery together, and reconcile deletion markers/outbox rows.
5. Re-run health, auth, cookie, gallery, upload, and deletion smoke tests.
6. Document whether `v1.0.0` shipped. Do not create a release tag for an unshipped or
   rolled-back deployment.
