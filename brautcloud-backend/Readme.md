# Brautcloud Backend

> Backend for Brautcloud, a Service where guest can share the pictures they took at a Wedding with the bride & groom. 

## Overview
This is a backend project built with **Java, SpringBoot, AiSto and Postgres**. 
Currently it's in early development.


## Getting started
```bash 
# Clone the repo
git clone https://github.com/domenicwalther/brautcloud-backend.git
cd brautcloud-backend
```

> **Note:** Docker Compose does not start Spring Boot. Run `docker compose up -d` manually, then start an AiStor container with its license mounted.

## Environment Variables
This project uses [Doppler](https://www.doppler.com/) to manage environment variables securely. For local development, activate Spring's `local` profile; it is the only non-test profile that permits an HTTP S3 endpoint and enables verbose security/Flyway logs.

### Required Variables

Add these variables in your Doppler project/config:

| Key | Description |
|-----|-------------|
| `SPRING_PROFILES_ACTIVE` | Set to `local` for local development. |
| `AWS_ACCESS_KEY_ID` | Access key for local S3 (AiStor). |
| `AWS_SECRET_ACCESS_KEY` | Secret key for local S3 (AiStor). |
| `AWS_ENDPOINT` | S3 endpoint, normally `http://127.0.0.1:9000` locally; HTTPS is required outside `local`/`test`. |
| `POSTGRES_URL` | JDBC URL, normally `jdbc:postgresql://127.0.0.1:5432/mydatabase` locally. |
| `POSTGRES_USER` | PostgreSQL username; Compose reads same variable. |
| `POSTGRES_PW` | PostgreSQL password; Compose reads same variable. |
| `JWT_SECRET` | JWT signing secret. |
| `APP_ALLOWED_ORIGINS` | Comma-separated browser origins, normally `http://localhost:4200` locally. |

`POSTGRES_DB` is optional for Compose and defaults to `mydatabase`. Never commit credential values. Compose binds PostgreSQL and AiStor ports to loopback only.

Start local dependencies with credentials exported:

```bash
export SPRING_PROFILES_ACTIVE=local
export POSTGRES_USER=brautcloud
export POSTGRES_PW='change-me-locally'
docker compose up -d
```

Included in this project is a Spring_Run.run.xml which automatically starts Doppler & Spring Boot. Ensure Doppler provides all required variables above.

## Running the backend test suite

Prerequisites for backend verification:

- GraalVM JDK 25 (the Maven compiler target and CI runtime are Java 25)
- Maven 3.9.12 through the checked-in Maven wrapper
- Docker Engine/Desktop or rootless Podman with a Docker-compatible socket
- permission to pull and run the pinned test image `postgres:16-alpine`

Spring Boot 4.0.2 manages Testcontainers 2.0.3. All PostgreSQL-backed tests use the same `postgres:16-alpine` policy; do not replace it with `latest` or another major version without updating the test support and this document.

No PostgreSQL installation, Doppler login, AWS credentials, or live S3-compatible service is needed. The suite exercises startup and Flyway migration checks, authentication/session flows, repository persistence, and cross-layer event/image journeys against PostgreSQL Testcontainers while replacing S3 operations with a test double.

Run the exact backend verification used by CI from a clean build:

```bash
cd brautcloud-backend
./mvnw -B clean verify
```

Docker users must have a reachable daemon before running the command:

```bash
docker info
```

For rootless Podman, expose its Docker-compatible socket first. Ryuk is disabled for this suite because its shared PostgreSQL container has an explicit shutdown hook:

```bash
systemctl --user enable --now podman.socket
DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock" \
  TESTCONTAINERS_RYUK_DISABLED=true \
  ./mvnw -B clean verify
```

If neither `docker info` nor the Podman socket is reachable, PostgreSQL-backed tests fail before the Spring context finishes booting. Focused unit and MVC slice tests can run without a container, for example:

```bash
./mvnw -B -Dtest=JwtServiceTest,EventControllerWebMvcTest test
```

Database integration tests intentionally use PostgreSQL through Testcontainers rather than H2 so constraints, UUIDs, migrations, cascade behavior, and SQL semantics match production.

Storage deployment requirements, bucket privacy assumptions, presign lifetimes, deletion retries, lifecycle, IAM, and backup ownership are documented in [STORAGE_OPERATIONS.md](STORAGE_OPERATIONS.md). The application accepts a configurable S3-compatible endpoint and does not prescribe a production provider.
