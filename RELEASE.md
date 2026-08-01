# Release identity

## Planned release

| Field | Value |
|---|---|
| Product | Brautcloud |
| Version | `1.0.0` |
| Git tag | `v1.0.0` |
| Status | Planned; not shipped |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |
| Launch runbook | [RUNBOOK.md](RUNBOOK.md) |

`VERSION`, backend Maven metadata, frontend package metadata, this document, and
`CHANGELOG.md` define one release identity. This repository does not claim that
`v1.0.0` is deployed or available to users.

## Tagging policy

Create annotated tag `v1.0.0` only after the release owner confirms:

1. CI passed on the release commit.
2. A deployment target and owner were approved outside this repository.
3. Runtime secrets, TLS, database backup, storage policy, and rollback steps are ready.
4. Smoke tests passed against the selected target.

Example, after approval:

```bash
git tag -a v1.0.0 -m "Brautcloud 1.0.0"
git push origin v1.0.0
```

Do not push this tag as part of documentation work. A tag is a shipped-release
marker, not a promise that Compose or this repository deploys production.

## Release checklist

- [ ] Review [CHANGELOG.md](CHANGELOG.md) and confirm no unshipped claims.
- [ ] Run frontend checks from `brautcloud-frontend`.
- [ ] Run `./mvnw clean verify` from `brautcloud-backend` with PostgreSQL Testcontainers available.
- [ ] Supply production API/origin configuration; reject localhost and HTTP endpoints.
- [ ] Complete target-neutral prerequisites and target-specific owner checklist in [RUNBOOK.md](RUNBOOK.md).
- [ ] Record migration and rollback plan before deployment.
- [ ] Deploy through the separately approved target process.
- [ ] Run smoke tests, then create and publish `v1.0.0`.
