# Continuous integration

GitHub Actions runs independent frontend, backend, and secret-scan checks for every pull request and for pushes to `main`.

- Secret scan runs `scripts/check-secrets.py` against tracked files and fails on high-confidence credential material.
- Frontend uses Node.js 22 LTS and frozen pnpm installs, then validates formatting, strict TypeScript checks, production dependency security (`pnpm audit --prod` fails on high/critical advisories), unit tests, and production build. All checks are read-only.
- Backend uses GraalVM JDK 25 and Maven cache, then runs `./mvnw -B clean verify` for formatting validation, compilation, packaging, and the full Testcontainers-backed test suite on GitHub-hosted Ubuntu. Local Docker and rootless Podman prerequisites are documented in [`brautcloud-backend/Readme.md`](brautcloud-backend/Readme.md).
- Supply-chain checks independently verify Maven wrapper and dependency checksums, scan backend dependencies with OSV-Scanner, and publish a CycloneDX JSON SBOM. These checks do not modify repository files or dependencies.

Continuous delivery is intentionally absent. No deployment target has been selected yet; deployment workflows will be added only after that decision.
