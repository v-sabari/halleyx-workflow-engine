# Contributing to Halleyx Workflow Engine

This is a single repository containing both backend and frontend — there are no separate repos to clone.

## Local Setup

### Backend

```bash
cd backend
cp ../.env.example .env   # fill in real values
./mvnw clean install
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

⚠️ Before relying on `npm run lint`, see the ESLint config/dependency mismatch noted in the root `README.md` — `eslint.config.js` currently imports a plugin (`eslint-plugin-react-refresh`) that isn't in `package.json`'s `devDependencies`, and uses a flat-config helper (`eslint/config`) that requires ESLint 9, while `package.json` pins ESLint 8.57.0.

## Branching

Branch off `main`. Suggested naming: `feature/<short-description>`, `fix/<short-description>`, `chore/<short-description>` — no naming convention was specified in any provided file, so use this or your team's existing convention.

## Commit Messages

No commit convention was specified in the provided files. [Conventional Commits](https://www.conventionalcommits.org/) is suggested here as a default, not something already in use:

```
feat: add retry backoff to execution service
fix: correct rate limit bucket refill rate
docs: update API reference for audit logs
```

## Before Opening a Pull Request

- Backend: `./mvnw test` (run from `backend/`) must pass. If you touch `security/`, `ratelimit/`, or `idempotency/` (all under `backend/src/main/java/com/halleyx/workflow_engine/`), add or update tests — these are the safety-critical paths (API key auth, bootstrap secret, rate limiting, idempotent retries).
- Frontend: run `npm run lint` (see the known ESLint issue above) and `npm run build`.
- Fill out `.github/PULL_REQUEST_TEMPLATE.md` completely.

## Code Style

No style guide (e.g. Google Java Style, Airbnb JS) was specified in any provided file. This section intentionally left without invented conventions — adopt one explicitly if you want it enforced, and I can wire it into the linter/formatter config once you tell me which.

## Security Issues

Do not open a public issue for security vulnerabilities — see [SECURITY.md](SECURITY.md).
