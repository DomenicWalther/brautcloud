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
