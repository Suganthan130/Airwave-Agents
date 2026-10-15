# Project Rules

Conventions for working in the Airwave Agents repository.

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

## Robustness — handle every state

Every function or feature that does real work (async especially) must handle **all** of:

- **Loading** — show an indicator and block the trigger while it runs.
- **Success** — handle explicitly.
- **Error** — catch everything; show a clear, user-friendly message (never a raw error or silent fail).
- **Edge cases** — invalid/empty input, no network, cancellation, already-in-progress, missing config.

The happy path alone is not "done". See `CLAUDE.md` → "Handle every state" for the full rule.

## Workflow

- Branch from `main`; open a pull request for every change.
- Keep PRs small and focused; fill out the PR template.
- Add or update tests and docs alongside code changes.
