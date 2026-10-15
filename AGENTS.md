# AGENTS.md

Guidance for any AI coding agent (Antigravity, Claude Code, Cursor, etc.) working in
this repo. Read this first. For full status and what's already built, see
[`docs/planning/BUILD-LOG.md`](docs/planning/BUILD-LOG.md).

## Project

**Airwave Agents** — AI Builder Cup 2026 entry. A Gemini-powered AI radio co-host on an
Icecast/Shoutcast stack. Monorepo with two clients + a backend:

- `apps/android/transmiter/` — Android transmitter app (Kotlin + Compose + native C++). **Built.**
- `apps/web/` — listener web app. *Scaffold.*
- `services/agent-api/` — Gemini co-host backend (Cloud Run, Python). *Scaffold.*
- `apps/android/core-app/` — private git submodule (the original proprietary app).

Default to the latest, most capable Gemini / Claude models. Keep AI/LLM provider calls
in `services/agent-api/`, never in the clients.

## Public / private split — do not break this

The public repo must never expose the proprietary engine, the reproduced UI of the
existing app, or any credentials. The seam is already built; respect it:

- **Private (git-ignored, local only — never commit):**
  - Native engine: `apps/android/transmiter/app/src/main/cpp/**`, `AudioStreamer.kt`,
    `RealBroadcastEngine.kt`, `PrivateExperience.kt`.
  - Reproduced screens: `ui/screens/{HomeScreen,MixerScreen,EqualizerScreen,LibraryScreen,SettingsScreen}.kt`.
  - `google-services.json`, keystores (`*.jks`/`*.keystore`), all stream credentials, `.env`.
- **Public (committed):** the generic `BroadcastEngine` interface, `StreamConfig`,
  `Playlist`, `ExperienceRegistry`, the auth screens, `MainActivity`, theme, AI/web scaffolds.
- The app builds **both ways**: `MainActivity` wires through registries and reflectively
  calls `PrivateExperience.install()`. The native Gradle build is gated on the private
  `cpp/` existing (`hasNativeEngine`), so a public checkout compiles native-free with a
  placeholder + no-op engine. Keep it that way — never add a public reference to a private file.

Before committing, verify no secret is tracked (e.g. `git grep <value>` returns nothing).

## Hard rules

- **Never commit secrets** — they belong in git-ignored files or the environment. See `RULES.md`.
- **The core app stays private** — `apps/android/core-app/` is a submodule; commit the
  pointer only, never its source, and don't edit inside the checkout as public-repo work.
- **Handle every state** — any function that does real work (async: auth, network,
  Firestore, audio/IO, streaming) must handle **loading, success, error (clear user-facing
  message), and edge cases** (empty/invalid input, no network, cancellation, timeout,
  missing config). The happy path alone is not "done." Reuse existing helpers
  (`AuthErrors`, loading overlays, the `withTimeoutOrNull` pattern). Full rule in `CLAUDE.md`.
- **Surgical changes, simplicity first** — match surrounding style; no speculative abstraction.

## Build / run (transmitter app)

```
cd apps/android/transmiter
./gradlew :app:assembleDebug          # builds native engine (arm64) when cpp/ is present
./gradlew :app:installDebug           # install to a connected device
```

- Needs the Android SDK + NDK; CMake 3.22.1; Oboe via `prefab`.
- `google-services.json` must be present at `app/` for the Google Services plugin (git-ignored).
- Devices used so far are arm64 (Pixel, Redmi); add more ABIs for release.

## Design

Light "professional blue" theme (`#2563EB`), Inter, brand logo. Cards get subtle elevation
+ hairline; list rows stay flat; 4dp grid. Not a flashy/neon AI look.
