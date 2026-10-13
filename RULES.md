# Project Rules

Conventions for working in the Airwave Agents / OnAir AI repository.

## Secrets

- Never commit secrets: `.env` files, API keys, service-account JSON,
  `google-services.json`, keystores (`.jks`, `.keystore`), or `.firebaserc`.
- Load all credentials from the environment at runtime.

## Code organization

- Backend code lives in `services/agent-api/`.
- Web client lives in `apps/web/`.
- Android integration (not the app itself) lives in `integrations/android/`.
- Infrastructure config lives in `infra/`.
- The private Android app source is kept out of this repository.

## Workflow

- Branch from `main`; open a pull request for every change.
- Keep PRs small and focused; fill out the PR template.
- Add or update tests and docs alongside code changes.
