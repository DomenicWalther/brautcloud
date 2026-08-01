# Continuous integration

GitHub Actions runs independent frontend and backend checks for every pull request and for pushes to `main`.

- Frontend uses Node.js 22 LTS and frozen pnpm installs, then validates formatting, strict TypeScript checks, production dependency security (`pnpm audit --prod` fails on high/critical advisories), unit tests, and production build. All checks are read-only.
- Backend uses GraalVM JDK 25 and Maven cache, then runs `./mvnw -B clean verify` for formatting validation, compilation, packaging, and the full Testcontainers-backed test suite on GitHub-hosted Ubuntu. Local Docker and rootless Podman prerequisites are documented in [`brautcloud-backend/Readme.md`](brautcloud-backend/Readme.md).

Continuous delivery is intentionally absent. No deployment target has been selected yet; deployment workflows will be added only after that decision.
