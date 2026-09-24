# Configuration Rule Engine

A small portfolio application for exploring relationships between product features.
It uses a generic catalog model so the same rule concepts can describe laptops or other products.

Visitors can start a private guest workspace without registering. It lasts four hours from
creation, survives page refreshes and application restarts, and is deleted after it expires.
The public showcase is read-only and needs no session. At most 50 new guest workspaces can be
started in any rolling hour across all visitors.

Each guest edits a private copy of an 11-feature laptop catalog. A relationship has one source
feature, one type, and one to ten target features. For source A and target B:

| Type | Meaning |
| --- | --- |
| `REQUIRES` | Choosing A also requires B. |
| `REQUIRED_WITH` | Choosing B also requires A. |
| `NOT_ALLOWED_WITH` | A and B can't be chosen together. |

Changes are staged in a pending batch of up to 32 changes, checked together, and applied in one
step. A check lists added and removed relationships, new indirect requirements, anything that
would make a feature impossible to choose, and the exact number of valid configurations before and
after. Applying is safe to retry and refuses a batch whose check is out of date. A configuration
tester explains why a chosen set of features is or isn't allowed. The showcase holds read-only
laptop and fictional automotive examples.

A guest can also describe a rule in plain English. The assistant only fills the relationship form
for review; nothing is staged until the guest stages it. With `OPENAI_API_KEY` set on the server,
one OpenAI request with a 10-second timeout and no retries proposes a source, type, and up to ten
targets. The model returns only those fields, never free text, so anything else, including
unrelated requests, gets the fixed help text. `OPENAI_MODEL` picks the model (default
`gpt-5.6-luna`).
Each guest workspace gets at most 10 live requests, and the deployment at most 100 per UTC day.
Every answer is checked with the same rules as manual entry. Without a key, a clearly labeled
example parser understands only "A requires B", "A is required with B", and "A can't be chosen
with B" (also "cannot be chosen with" and "is not allowed with"), with exact feature names and one
target. It also steps in when a live request fails or a limit is reached. Manual editing always
works.

Session and CSRF cookies are always `Secure`. Browsers accept them over plain HTTP only on
loopback addresses such as `127.0.0.1` and `localhost`, so serve any other address over HTTPS.

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
Keep database passwords and the OpenAI key outside Git. To run the one live OpenAI check, which
costs money and is skipped otherwise:

```sh
CRE_LIVE_AI_SMOKE=true OPENAI_API_KEY=... ./mvnw -B verify -Dit.test=OpenAiLiveSmokeIT
```
