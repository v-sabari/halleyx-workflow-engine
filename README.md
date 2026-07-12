# Halleyx Workflow Engine

A rule-driven workflow execution engine. Backend: Spring Boot 3.3.4 / Java 21. Frontend: React 18 + Vite. Both live in this single repository.

## Repository Layout

```
.
├── backend/                     # Spring Boot backend (renamed from workflow-engine/)
│   ├── src/main/java/com/halleyx/workflow_engine/
│   │   ├── config/               # AsyncConfig, CorsConfig, RestClientConfig
│   │   ├── controller/           # WorkflowController, StepController, RuleController,
│   │   │                         #   ExecutionController, NotificationController,
│   │   │                         #   AuditLogController, HealthController
│   │   ├── entity/                # Workflow, Step, Rule, Execution, ExecutionLog, Notification
│   │   ├── exception/             # GlobalExceptionHandler, BusinessException, ResourceNotFoundException
│   │   ├── idempotency/           # IdempotencyService, IdempotencyRecord, IdempotencyCleanupTask
│   │   ├── ratelimit/              # RateLimitFilter, RateLimitConfig (Bucket4j)
│   │   ├── repository/            # Spring Data JPA repositories
│   │   ├── security/               # ApiKey, ApiKeyAuthenticationFilter, ApiKeyManagementController,
│   │   │                           #   ApiKeyService, SecurityConfig
│   │   └── service/                 # WorkflowService, StepService, RuleService, RuleEvaluationService,
│   │                                #   ExecutionService, AsyncExecutionService, ExecutionLogService,
│   │                                #   NotificationService, InputSchemaValidatorService
│   ├── src/test/java/...          # Unit/integration tests (JUnit 5, H2 for context tests)
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd / .gitattributes / .gitignore
│   └── load-test.js, health-check-load-test.js, stress-test.js,
│       graceful-shutdown-test.js, db-connection-test.js   # k6 scripts
│
├── frontend/                    # React 18 + Vite frontend (renamed from FrontEnd/)
│   ├── src/
│   │   ├── components/            # AppShell, Header, Sidebar, NotificationBell
│   │   ├── pages/                 # WorkflowList, WorkflowEditor, WorkflowExecution,
│   │   │                          #   StepRuleEditor, AuditLog
│   │   ├── routes/                 # Editor, Execute, Rules, Audit, sitemap[.]xml.js
│   │   ├── services/api.js         # axios instance — reads VITE_API_URL / VITE_API_KEY
│   │   ├── styles/                 # per-page CSS + design tokens
│   │   ├── App.jsx, main.jsx
│   │   └── .env.example            # currently empty (see Known Issues below)
│   ├── public/
│   ├── package.json, package-lock.json
│   ├── vite.config.js, eslint.config.js, vercel.json, index.html
│
├── .env.example                 # backend env vars (see below)
├── .gitignore
├── docker-compose.yml
├── README.md, CONTRIBUTING.md, SECURITY.md, CODE_OF_CONDUCT.md, LICENSE, CHANGELOG.md
└── .github/
    ├── workflows/ci.yml
    ├── ISSUE_TEMPLATE/
    ├── PULL_REQUEST_TEMPLATE.md
    └── dependabot.yml
```

> This layout is the result of renaming `workflow-engine/` → `backend/` and `FrontEnd/` → `frontend/` for clarity, and removing a handful of accidentally-committed artifacts (see **Repo Cleanup Notes** below). No application source code was modified.

## Tech Stack (as declared in `backend/pom.xml` / `frontend/package.json`)

**Backend** — Spring Boot 3.3.4, Java 21
- `spring-boot-starter-web`, `-data-jpa`, `-validation`, `-mail`, `-security`, `-actuator`
- `mysql-connector-j` (runtime scope)
- `bucket4j-core` 8.10.1 — in-JVM, `ConcurrentHashMap`-backed rate limiting (no Redis/Hazelcast)
- `jackson-datatype-jsr310` — registered in `RestClientConfig` so `LocalDateTime` serializes as ISO-8601
- Lombok (optional, excluded from the repackaged JAR)
- Test scope: `spring-boot-starter-test`, `spring-security-test`, `h2` (H2 only used by `application-test.properties`, never on the production classpath)

**Frontend** — `frontend/package.json`
- React 18.3.1, `react-dom` 18.3.1, `react-router-dom` 6.24.0
- Axios 1.7.2
- Vite 5.3.4, `@vitejs/plugin-react` 4.3.1
- ESLint 8.57.0 (see **Known Issues** below)

## Getting Started

### Prerequisites
- Java 21 (JDK)
- Maven (or the bundled `./mvnw`)
- Node.js + npm
- A MySQL instance

### Backend

```bash
cd backend
cp ../.env.example .env   # fill in real values, or export as real env vars
./mvnw clean install
./mvnw spring-boot:run
```

Starts on `http://localhost:8080` by default (`server.port=${PORT:8080}`).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

`frontend/src/services/api.js` reads `VITE_API_URL` (falls back to `http://localhost:8080/api/v1`) and `VITE_API_KEY` (injected as the `X-API-Key` header on every request if set).

## Known Issues (found during this restructure, not fixed — no code was changed)

1. **ESLint config/dependency mismatch.** `frontend/eslint.config.js` imports `eslint-plugin-react-refresh` and uses `defineConfig`/`globalIgnores` from `eslint/config` — both require ESLint 9. But `frontend/package.json` pins `eslint@^8.57.0` and never lists `eslint-plugin-react-refresh` in `devDependencies` (it lists `eslint-plugin-react` instead, which the config never imports). `npm run lint` will fail as shipped. Fix by either upgrading to ESLint 9 + adding the missing plugin, or rewriting `eslint.config.js` for ESLint 8 against the plugins actually installed.
2. **`frontend/src/.env.example` is empty (0 bytes)** and lives inside `src/` rather than the frontend project root, where Vite actually looks for env files. The two real variables the frontend reads are documented in the root `.env.example` instead — consider moving/populating this file yourself.

## Repo Cleanup Notes (what changed vs. the raw upload, and why)

- `workflow-engine/` → `backend/`, `FrontEnd/` → `frontend/` (rename only).
- Removed `frontend/eslint.config.zip`, `frontend/package-lock.zip`, `frontend/src.zip` — these were upload archives that had been accidentally committed into the repo.
- Removed an unrelated image (`WhatsApp Image 2026-03-21 at 1.15.02 PM.jpeg`) that had been accidentally committed under `frontend/src/styles/`.
- Removed a stray, empty root-level `package-lock.json` (no root `package.json` exists to justify it).
- Removed `frontend/README.md` and `frontend/.gitignore` — these were the default Vite scaffold files, superseded by this repo's root `README.md`/`.gitignore`.
- Removed `backend/.idea/` (IntelliJ project files) — not tracked by git in the original repo, so excluded from this clean copy.
- Removed `frontend/dist/` (build output) and `frontend/src/.env.local` (a real local file explicitly marked "DO NOT COMMIT" inside itself) — neither was tracked by git in the original repo.
- This delivered archive does not include `.git/` history. If you want to preserve commit history while renaming, run `git mv workflow-engine backend` and `git mv FrontEnd frontend` in your existing clone instead of replacing it with this zip.

## Environment Variables

See `.env.example` at the repo root for every backend variable (all read in `backend/src/main/resources/application.properties`), including `SPRING_DATASOURCE_*`, `SPRING_MAIL_*`, `APP_BOOTSTRAP_SECRET`, `CORS_ALLOWED_ORIGINS`, and the async executor tuning variables. Frontend variables (`VITE_API_URL`, `VITE_API_KEY`) are documented at the bottom of the same file.

## API Reference (from actual `@RequestMapping`/`@*Mapping` annotations)

| Method | Path | Controller |
|---|---|---|
| `POST` | `/api/v1/workflows` | `WorkflowController` |
| `GET` | `/api/v1/workflows` | `WorkflowController` |
| `GET` | `/api/v1/workflows/{id}` | `WorkflowController` |
| `PUT` | `/api/v1/workflows/{id}` | `WorkflowController` |
| `DELETE` | `/api/v1/workflows/{id}` | `WorkflowController` |
| `POST` | `/api/v1/workflows/{workflowId}/execute` | `WorkflowController` |
| `POST` | `/api/v1/workflows/{workflowId}/steps` | `StepController` |
| `GET` | `/api/v1/workflows/{workflowId}/steps` | `StepController` |
| `PUT` | `/api/v1/steps/{id}` | `StepController` |
| `DELETE` | `/api/v1/steps/{id}` | `StepController` |
| `POST` | `/api/v1/steps/{stepId}/rules` | `RuleController` |
| `GET` | `/api/v1/steps/{stepId}/rules` | `RuleController` |
| `PUT` | `/api/v1/rules/{id}` | `RuleController` |
| `DELETE` | `/api/v1/rules/{id}` | `RuleController` |
| `POST` | `/api/v1/executions/start` | `ExecutionController` |
| `GET` | `/api/v1/executions` | `ExecutionController` |
| `GET` | `/api/v1/executions/{id}` | `ExecutionController` |
| `GET` | `/api/v1/executions/{id}/logs` | `ExecutionController` |
| `POST` | `/api/v1/executions/{id}/cancel` | `ExecutionController` |
| `POST` | `/api/v1/executions/{id}/retry` | `ExecutionController` |
| `POST` | `/api/v1/executions/{id}/approve` | `ExecutionController` |
| `POST` | `/api/v1/executions/{id}/reject` | `ExecutionController` |
| `POST` | `/api/v1/keys/issue` | `ApiKeyManagementController` — requires `X-Bootstrap-Secret` header |
| `DELETE` | `/api/v1/keys/{id}` | `ApiKeyManagementController` |
| `GET` | `/health`, `/api/v1/health` | `HealthController` |
| `GET` | `/notifications` | `NotificationController` |
| `GET` | `/notifications/unread` | `NotificationController` |
| `PUT` | `/notifications/{id}/read` | `NotificationController` |
| `PUT` | `/notifications/read-all` | `NotificationController` |
| `GET` | `/audit-logs` | `AuditLogController` — note: not under `/api/v1`, unlike every other controller |
| `GET` | `/actuator/health` | Spring Boot Actuator |

Authenticated requests must send the raw key in the `X-API-Key` header (see `ApiKeyAuthenticationFilter.API_KEY_HEADER`). Key issuance additionally requires `X-Bootstrap-Secret: <APP_BOOTSTRAP_SECRET>`.

## Testing

```bash
cd backend
./mvnw test
```

Test classes present: `WorkflowEngineApplicationTests`, `IdempotencyServiceTest`, `RateLimitFilterTest`, `ApiKeyServiceTest`, `AsyncExecutionServiceTest`, `InputSchemaValidatorServiceTest`, `RuleEvaluationServiceTest`, `RuleServiceTest`, `WorkflowServiceTest`. Context-load tests use H2 via `application-test.properties`.

No frontend test runner is configured — there is no `npm test` script in `frontend/package.json`.

## Load Testing

Five k6 scripts live in `backend/`: `load-test.js`, `health-check-load-test.js`, `stress-test.js`, `graceful-shutdown-test.js`, `db-connection-test.js`.

```bash
k6 run backend/health-check-load-test.js
```

## Linting

```bash
cd frontend
npm run lint
```

See **Known Issues** above before assuming this works out of the box.

## Docker

```bash
cd backend
docker build -t halleyx-workflow-engine .
docker run -p 8080:8080 --env-file ../.env halleyx-workflow-engine
```

Two-stage build: `maven:3.9.9-eclipse-temurin-21` → `mvn clean package -DskipTests`, then `eclipse-temurin:21-jre` running `java -jar app.jar`, exposing port 8080.

Or bring up backend + MySQL together from the repo root:

```bash
docker compose up --build
```

## Deployment

`backend/src/main/resources/application.properties` sets `spring.main.lazy-initialization=true`, with a comment indicating this is specifically to reduce cold-start time on CPU-throttled free-tier PaaS hosting. `frontend/vercel.json` (a SPA rewrite rule) indicates the frontend is intended for Vercel; no specific backend host is configured in code beyond that comment.

## License

See [LICENSE](LICENSE).
