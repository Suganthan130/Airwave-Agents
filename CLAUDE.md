# CLAUDE.md

Guidance for AI coding assistants (and humans) working in this repo. Behavioral guidelines to
reduce common mistakes, plus Airwave Agents project specifics.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

---

## Project context

**Airwave Agents** is a Gemini-powered AI radio co-host that helps stations
stay live 24/7, engaging listeners in their own language, on top of our Icecast/Shoutcast
streaming stack.

**Two clients:**
- **Android app (`integrations/transmiter/`) — the transmitter.** Broadcasters push the live
  stream (co-host mixed in) to Icecast/Shoutcast. Kotlin + C++/NDK.
- **Web app (`apps/web/`) — for listeners.** The audience tunes in from the browser.

**Stack:** Gemini, Cloud Run, Firestore, Firebase, Android (Kotlin + C++), Icecast/Shoutcast.

**Where things live:**
- `services/agent-api/` — backend co-host brain (Python, Cloud Run). Source under
  `src/airwave_agents/{api,core,services,models}/`, tests under `tests/`.
- `apps/web/` — listener web app; static assets under `public/`.
- `integrations/transmiter/` — Android transmitter app (public).
- `integrations/core-app/` — **private git submodule** (`BroadcastMySelf-Radio-Tool`). See below.
- `infra/` — `firebase/` and `gcp/` config. `scripts/` — ops/dev scripts. `docs/` — documentation.

When adding to the LLM/Gemini layer, default to the latest Claude/Gemini models and keep
provider calls in `services/agent-api/`, not in clients.

## Hard rules (project-specific)

- **Never commit secrets:** `.env`, API keys, `*service-account*.json`, `google-services.json`,
  keystores (`.jks`, `.keystore`), `.firebaserc`, `local.properties`. The root and
  `integrations/transmiter/` `.gitignore` files already cover these — don't weaken them.
- **The core app stays private.** `integrations/core-app/` is a submodule; this repo commits only
  a pointer, never its source. Do not copy the private app's source into this repo, and do not
  make changes inside the submodule checkout as part of public-repo work.
- **Keep clients secret-free.** Credentials load from the environment/server at runtime, never
  from the web or Android clients.
- See `RULES.md` for workflow and `SECURITY.md` for disclosure.

## Handle every state — no exceptions

Any function or feature that does real work (especially async: auth, network, Firestore, file/audio
I/O, streaming) **must handle all of these before it's considered done**:

- **Loading:** show a loading indicator and block/disable the triggering control while work is in
  flight. Never let a user fire the same action twice or wonder if anything happened.
- **Success:** handle it explicitly (navigate, confirm, update state).
- **Error:** catch every failure and show a **clear, user-friendly message** mapped from the cause
  (e.g. wrong password, email already in use, no network, provider not configured). Never surface a
  raw stack trace, a cryptic code, or a silent failure.
- **Edge cases:** empty/invalid input (validate before calling out), no network, user cancellation
  (treat as benign, not an error), operation already in progress, and missing config.

"It compiles" or "the happy path works" is **not** done. If you add a function, you own all four
states. Reuse the shared helpers (e.g. `AuthErrors`, loading overlays) rather than re-inventing them.

---

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require
constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to
overcomplication, and clarifying questions come before implementation rather than after mistakes.
