# Continuous integration

GitHub Actions runs independent frontend and backend checks for every pull request and for pushes to `main`.

- Frontend uses Node.js 22 LTS and frozen pnpm installs, then validates formatting, unit tests, and production build.
- Backend uses GraalVM JDK 25 and Maven cache, then runs formatting validation, compilation, packaging, and the full Testcontainers-backed test suite on GitHub-hosted Ubuntu.

Continuous delivery is intentionally absent. No deployment target has been selected yet; deployment workflows will be added only after that decision.
