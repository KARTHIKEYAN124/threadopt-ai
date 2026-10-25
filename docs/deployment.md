# Deployment

The complete stack is containerized. The backend uses file-backed H2 locally and PostgreSQL in Compose. The AI service and database have no public ports. Only predefined bounded workloads execute. The Nginx entry point limits experiment requests and the backend admits one benchmark at a time globally.

## Local Docker

Copy `.env.example` to `.env`, set a unique DB_PASSWORD, then run from the root:

```sh
docker compose -f infrastructure/compose.yaml --env-file .env up -d --build
```

Open http://localhost:3000. Verify `/api/health`, run an experiment, check history after restarting the stack, and verify live metrics. Models are baked into the AI image; retrain and rebuild the AI image to update them.

## Public VPS

On your Linux server install Docker Engine, Compose, and Caddy. Copy the repository and your untracked `.env` there. Start Compose using the command above. Point your domain's DNS to the server. Install `infrastructure/Caddyfile` as the system Caddy configuration, set DOMAIN to the real hostname in the Caddy service environment, and restart Caddy. Allow inbound 80/443; port 3000 remains bound to loopback. Caddy terminates HTTPS and proxies WebSockets.

For a private academic demonstration, enable Caddy `basic_auth` with a generated password hash before exposing the application. The checked-in application has no user accounts or per-user budgets. The global admission limit and Nginx rate limit are prototype controls, not multi-tenant isolation.

## Release and rollback

CI verifies Java, Python, TypeScript, and images. Deployment is deliberately manual until a repository, registry, server, and credentials are configured. Tag an approved commit; build and push immutable images for that tag; record the current image digests; update Compose to those digests and run `up -d`. Verify health, WebSockets, experiment execution, and persistence. Roll back by restoring the previous digests and running `up -d`. Back up PostgreSQL with pg_dump before releases; preserve the pgdata volume.

No cloud account, domain, remote Git repository, or production credentials are configured by this project. Container runtime availability and real-server smoke tests are tracked in the validation report.
