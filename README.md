# Configuration Rule Engine

A small portfolio application for exploring relationships between product features.
It uses a generic catalog model so the same rule concepts can describe laptops or other products.

The current application serves the browser shell and local database-backed runtime;
catalog editing and rule checking are not available yet.

## Run locally

You need Java 25 and Docker. Maven and Node are downloaded by the Maven wrapper and build.
To run frontend commands directly, install Node 24 and run `npm ci` in `frontend` first.

To build and run all checks, install Chromium once for Playwright, then verify:

```sh
./mvnw -B -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium" \
  org.codehaus.mojo:exec-maven-plugin:3.6.3:java
./mvnw -B verify
```

For the containerized app, copy `.env.example` to `.env` and replace the example password.
Then run:

```sh
docker compose up --build -d
docker compose ps
```

Open <http://127.0.0.1:8080/>. Set `CRE_HTTP_PORT` if that loopback port is occupied.
PostgreSQL has a named data volume and no published host port.

For an isolated smoke test, set a unique `COMPOSE_PROJECT_NAME`;
existing volumes are not deleted.

## Development checks

Write a failing test for each behavior change, then implement it and rerun the test.
Apply formatting after editing Java or frontend code:

```sh
./mvnw -B spotless:apply
cd frontend && npm run format
```

`./mvnw -B verify` checks Google Java Format, Oxfmt, Oxlint, Angular tests, Java tests,
database startup, and the packaged browser routes.
Keep database passwords and any future provider keys outside Git.
