# Security Policy

## Reporting a Vulnerability

Do not open a public GitHub issue for security vulnerabilities. Use GitHub's private vulnerability reporting under the **Security** tab, or email a security contact you designate (none was specified in any provided file — add one here before publishing this repo).

## Project-Specific Security Notes (verified against the actual code)

- **API key authentication**: `ApiKeyAuthenticationFilter` reads the raw key from the `X-API-Key` header on every request (`API_KEY_HEADER = "X-API-Key"`).
- **Bootstrap secret for key issuance**: `POST /api/v1/keys/issue` (`ApiKeyManagementController`) requires a valid `X-Bootstrap-Secret` header matching `APP_BOOTSTRAP_SECRET`. Per the code comment in `ApiKeyManagementController`, this was previously an unauthenticated endpoint and the fix now requires the caller-supplied header; if `APP_BOOTSTRAP_SECRET` is unset or blank, the endpoint fails closed and rejects all requests. This value must be set only as a server-side environment variable and never bundled into the frontend build.
- **Rate limiting**: `RateLimitFilter` uses Bucket4j with in-JVM, `ConcurrentHashMap`-backed buckets (`bucket4j-core`, no Redis/Hazelcast extension). This means limits are **per instance** — if the backend is ever scaled horizontally, limits will not be shared across instances unless this is changed to a distributed backend.
- **CORS**: `app.cors.allowed-origins` defaults to `http://localhost:*,http://127.0.0.1:*` for local dev (`CorsConfig`); this must be overridden via `CORS_ALLOWED_ORIGINS` to your real frontend origin(s) in production.
- **Actuator exposure**: only `management.endpoints.web.exposure.include=health` is set, and `management.endpoint.health.show-details=never` — no other actuator endpoints or detail leakage are exposed.
- **Credentials**: database (`SPRING_DATASOURCE_*`) and mail (`SPRING_MAIL_*`) credentials are injected via environment variables only; none are hardcoded in `application.properties`.
- **Logging**: `SHOW_SQL` defaults to `false` and `SQL_LOG_LEVEL` defaults to `WARN`, so SQL statements are not logged by default in any environment.

## What I Did Not Find in the Code

No JWT, no 2FA/TOTP, no RBAC roles, no Sentry, no Twilio, no bcrypt password hashing, and no dependency-scanning plugin (e.g. OWASP Dependency-Check) are present in `pom.xml` or anywhere else in the provided files. If any of these exist elsewhere in your project, they weren't in the files uploaded to this conversation, so I haven't described them here.

## Dependency Updates

No Dependabot or Renovate config existed in the uploaded files. A `dependabot.yml` is included in this root-files set covering Maven (root) and npm (`frontend/`) — this is a new addition, not something extracted from your project.
