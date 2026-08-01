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

> **Note:** At this stage, the Docker Compose Spring setup is disabled. Make sure to run `docker compose up` manually and have an AiStor container running.

## Environment Variables
This Project uses [Doppler](https://www.doppler.com/) to manage environment variables securely.
Make sure you have Doppler installed and are logged in.

### Required Variables

Add these variables in your Doppler project/config:

| Key                   | Description |
|-----------------------|-------------|
| `AWS_ACCESS_KEY_ID`     | Access key for local S3 (aistore) |
| `AWS_SECRET_ACCESS_KEY` | Secret key for local S3 (aistore) |
| `AWS_ENDPOINT`          | Endpoint URL for the local S3 server |
| `POSTGRES_URL`          | Hostname or URL of the Postgres database |
| `POSTGRES_USER`         | Username for Postgres |
| `POSTGRES_PW`           | Password for Postgres |

> These variables are used by the AWS SDK in Java for interacting with the local aistore S3 server and by your backend for database connections.

Included in this Project is a Spring_Run.run.xml which automatically starts Doppler & Spring Boot.

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
