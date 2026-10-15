# Airwave Agents — Build Log

Progress record for the **AI Builder Cup 2026** entry. Summarizes what has been
built so far. No secrets, hosts, or credentials are recorded here.

Last updated: 2026-10-14

---

## 1. Overview

**Airwave Agents** is a Gemini-powered AI radio co-host built on an Icecast/Shoutcast
streaming stack. The deliverable is a monorepo with two clients and a backend:

- **Transmitter** — Android app broadcasters use to go live (built).
- **Listener web** — browser app for the audience (scaffolded).
- **agent-api** — Gemini AI co-host backend on Cloud Run (scaffolded).

Repo: `airwave-agents/` · GitHub: `Suganthan130/Airwave-Agents` (private).

---

## 2. Repository structure

```
airwave-agents/
├── apps/web/                      # Listener web app (scaffold)
├── services/agent-api/            # Gemini AI backend (scaffold)
├── integrations/
│   ├── transmiter/                # Android transmitter app (BUILT)
│   └── core-app/                  # Private submodule (proprietary app)
├── infra/{firebase,gcp}/          # Infra config (scaffold)
├── docs/ · scripts/ · logo/
└── README.md · CLAUDE.md · RULES.md · SECURITY.md · LICENSE
```

---

## 3. Public / private split (privacy model)

A core requirement: the public repo must not expose the proprietary engine, the
reproduced UI of the existing app, or any credentials. This is enforced with a
clean seam plus `.gitignore`:

- **Public (committed):**
  - The new competition code — `LoginScreen`, `SignUpScreen` (Google sign-in),
    `MainActivity`, theme, shared components.
  - Generic engine **contract** only: `BroadcastEngine` interface, `StreamConfig`,
    `Playlist`, `ExperienceRegistry` — concepts, no native names.
  - The AI/web scaffolds, infra, docs.
- **Private (git-ignored, local only):**
  - The native C++ engine (`cpp/**`), `AudioStreamer.kt`, `RealBroadcastEngine.kt`,
    `PrivateExperience.kt`.
  - The screens that reproduce the existing app: Home/Broadcast, Mixer, Equalizer,
    Library, Settings.
  - `google-services.json`, keystores, and all stream credentials.

**How it still builds both ways:** `MainActivity` renders through registries and
reflectively calls `PrivateExperience.install()`. Locally the real screens + native
engine plug in; in a public checkout those files are absent, so it compiles and runs
with a placeholder + no-op engine. The native Gradle build is gated on
`hasNativeEngine` (the private `cpp/` being present), so the public build is native-free.

---

## 4. Transmitter app — what's implemented

### Authentication (Firebase) — real
- **Email/password** sign-up and login via Firebase Auth.
- **Google Sign-In** via Credential Manager (needs Google provider + SHA-1 in Firebase).
- Session handling, sign-out from Settings, auth-state-driven navigation.
- Profile persisted to **Cloud Firestore** per account (non-blocking, background write).

### Broadcast console (Home) — real engine
- Status reflects the **real** engine connection state: OFFLINE / CONNECTING… /
  ON AIR / RECORDING (never a fake "connected").
- Go Live validates server config + requests the mic permission before starting;
  connect runs off the main thread (no ANR on a bad host); errors shown inline.
- Real **RTT**, **on-air timer**, bitrate. Record button. Stop / Pause while live.
- **Now Playing** with real track title/artist, seek, transport, and an **Up Next**
  queue driven by the real playlist:
  - Auto-advances to the next song in "Up Next" seamlessly when current song finishes (`musicPosition >= 0.99f`).
  - Playlist reordering with instant ↑ / ↓ arrow buttons and dialog controls (`Playlist.move`, `moveUp`, `moveDown`).
  - Tap queued item → modal with "Play now", reordering, and "Remove from queue".
- **AI Radio Jockey (RJ) Time Announcements**:
  - Live Gemini generation + Google Cloud TTS realistic RJ voiceover (`rjLine`).
  - Context-aware lines (announces current track, time check, and tease for up-next track).
  - Configurable RJ voice styles (Warm Male Host, Energetic Female Host, Studio Pro).
  - Configurable speaking speed (0.8x Relaxed, 1.0x Normal, 1.15x Radio Fast, 1.3x High Energy).
  - Manual on-demand button ("🎙️ Time Check") on the broadcast console.
  - Automated periodic scheduling (:00, :15, :30, :45 clock boundaries while live on-air).
- **AI Flash News & Breaking News Bulletins (`FlashNews` & `NewsManager`)**:
  - Dedicated **AI Co-Host & News Tab** on the main navigation bar (`AiStudioScreen`).
  - Real-time Cloud Firestore synchronization of 12+ curated global and tech news stories under the `news` collection with real-time updates and offline fallbacks.
  - **1-Minute Live Broadcast Auto-Popup**: When broadcaster goes live on-air (`connState == 1`), automatically triggers a breaking news alert modal every 1 minute cycling through stories.
  - **"Play Now (Pause & Resume)"**: Seamlessly voices bulletin in background without dead air, pauses current song, plays news on-air, and automatically resumes music when bulletin finishes.
  - **"Add to Up Next"**: Voices bulletin and places it into Up Next for smooth sequential playback after the active track.
  - **"🎧 Listen / Preview"**: Broadcasters can listen to the generated news bulletin locally through their device before putting it on air.
  - Gemini 2.5 Flash crafts a 3-part live radio script with breaking alert stinger, 2 factual news sentences, and a smooth radio outro sign-off returning listeners to the music.
  - Synthesized via Google Cloud TTS (44.1 kHz broadcast MP3 with SSML audio stinger).

### Background & Lock Screen Resilience (`BroadcastForegroundService`)
- **Android 14+ Compliant Foreground Service**: Declared with `microphone` and `mediaPlayback` foreground service types and corresponding runtime permissions.
- **CPU & Wi-Fi Keep-Alive**: Acquires `PARTIAL_WAKE_LOCK` and high-performance `WIFI_MODE_FULL_HIGH_PERF` lock so broadcasting and Icecast streaming never drop or freeze when the phone screen is locked.
- **Foreground Touch Initiation**: Starts directly in response to user gestures (`goLive()`, `startRecording()`) to comply with Android 14 foreground service start rules.
- **Fail-Safe & Non-Sticky**: Uses `START_NOT_STICKY` and guards `startForeground()` within `runCatching` to eliminate `ForegroundServiceStartNotAllowedException` crashes.
- **Persistent Lock Screen Notification**: Shows live track metadata, on-air status, and a 1-tap "Stop" action directly on the device lock screen.

### Mixer / Equalizer — real engine (private screens)
- Mic/Music faders + mute, monitor level/source, noise gate, auto-ducking, with live
  level meters.
- 10-band EQ, presets, bass boost, virtualizer.

### Library / music — real
- **Add tracks** via the system file picker; reads title/artist/duration.
- Plays through the native engine; shared playlist drives Home.
- Per-track **⋮ overflow** → actions dialog → **Add to Up Next** (confirm dialog) /
  Play now / Remove.

### Settings — real
- Account (Google), stream server config (type/host/port/mount/auth), audio quality,
  preferences. Saved to Firestore + applied to the live engine; timeouts on all
  network calls.

### Native engine (private)
- The proprietary C++ engine (AudioEngine, LAME, fdk-aac, Oboe DSP) + `AudioStreamer`
  ported into the private module; bound to the UI via `BroadcastEngine`. Builds for
  arm64, loads at runtime.

---

## 5. UI / UX

- Light "professional blue" theme (`#2563EB`), Inter, brand logo in launcher + headers.
- Dark status-bar icons for contrast; subtle elevation (soft shadow + hairline) on
  cards; flat list rows; consistent radii on a 4dp grid.
- Comprehensive handling per the project rule: loading overlays, input validation,
  mapped friendly error messages, cancellation, and timeouts.

---

## 6. Build / run

- Debug APK built with the Google Services plugin + native engine (arm64).
- Verified installed and running on two devices (Pixel 8, Redmi).

---

---

## 7. Listener Web App (`apps/web`) — BUILT & DEPLOYED

Live Web URL: `https://airwave-agents.web.app`

### Architecture & Tech Stack
- **Framework**: React 19 + Vite 6 + TypeScript.
- **Styling**: Tailwind CSS with custom brand theme tokens (professional blue `#2563EB`, dark studio cards, animated equalizer keyframes).
- **Hosting & Backend**: Firebase Hosting + Firebase Authentication + Cloud Firestore + Cloud Functions (Node.js 22, `us-central1`).

### Core Features
- **Live Radio Player**:
  - HTML5 audio streaming from Icecast/live broadcast streams with configurable mount URL.
  - Animated live audio visualizer equalizer bars, play/pause controls, volume slider, mute toggle, and status alerts.
  - Automatic audio ducking: when an AI RJ voice response plays, the background stream volume automatically ducks to 25% for pristine voice clarity.
- **Google Sign-In & Onboarding Profile**:
  - One-click Google Sign-In with Firebase Auth.
  - Profile modal prompt asking listener for **Name** and **Country** with flag icons.
  - Persisted to Cloud Firestore (`users/{uid}`).
- **Realtime Studio Chat**:
  - Live Firestore subscription on `messages` collection.
  - Displays listener avatars, display names, country badges, and messages.
  - Integrated AI RJ reply bubble with spoken text and an in-browser voice playback button (`🎧 Listen RJ Voice`).
- **20-Minute Cooldown with Live Countdown Timer**:
  - Enforces a 20-minute limit (1200s) between messages per listener to keep broadcasts balanced.
  - Real-time ticking countdown timer display (`e.g. 18m 42s left`) and animated cooldown progress bar.
  - Server-side rate enforcement via `sendChatMessage` Cloud Function.
- **AI Radio Co-Host Processing & Voice Synthesis**:
  - Backend Gemini 2.5 Flash + Google Cloud Text-to-Speech (`en-US-Neural2-D` warm male host).
  - Polite, warm radio host cadence personalizing each shoutout with listener name and country.
- **Broadcaster & Listener User Separation**:
  - **Two User Roles**: `broadcaster` (transmitting live on Android) and `listener` (tuning in from the web).
  - **Strictly 1 Station per Broadcaster**: Keyed directly to the broadcaster's `uid` (`stations/{uid}`), enforced by Firestore security rules (`allow write: if request.auth.uid == stationId`).
  - **Transmitter App Integration (`StationManager.kt`)**: Automatically registers and updates `stations/{uid}` on login, live broadcast state transitions, and track changes.
  - **Broadcaster UI in Transmitter**: Displays a dedicated "Your Public Listener Page" card in `HomeScreen` and `SettingsScreen` with the unique link (`https://airwave-agents.web.app/?station={uid}`), "📋 Copy Link", and "📤 Share Link" actions.
  - **Web Station Routing & Station Directory**:
    - URL query parameter `?station={uid}` tunes the web app directly to that broadcaster's live stream, now playing info, and chat.
    - "Browse Stations" directory modal allows listeners to discover and tune into any active broadcaster station.
    - User role badges in chat (`👑 Broadcaster` for the host, `🎧 Listener` for the audience).

---

## 8. Agent API (`services/agent-api`) — BUILT & DEPLOYED TO CLOUD RUN

Live Cloud Run URL: `https://agent-api-191983702527.us-central1.run.app`  
Integrated Web URL: `https://airwave-agents.web.app/api/v1`

### Architecture & Tech Stack
- **Framework**: Python 3.11 + FastAPI + Uvicorn + Pydantic v2.
- **Hosting & Infrastructure**: Google Cloud Run (`us-central1`), containerized via Dockerfile on Artifact Registry (`cloud-run-source-deploy`).
- **Unified Hosting Rewrite**: Configured in `firebase.json` so `https://airwave-agents.web.app/api/**` routes transparently to the Cloud Run `agent-api` service.
- **AI Models & Voice Engine**:
  - **Vertex AI Gemini 2.5 Flash**: Radio Co-Host scripts, on-air banter, breaking news scripting, multilingual listener chat comprehension, and 18+ safety filter.
  - **Google Cloud Text-to-Speech**: 44.1 kHz broadcast MP3 synthesis across regional voices (English, Tamil, Sinhala, Hindi, Spanish, French, German, Japanese, etc.).
  - **Firebase Admin SDK**: Direct Firestore access for chat message processing and station synchronization using Google Application Default Credentials (ADC).

### Endpoints
- `GET /` & `GET /health` — Service health and metadata checks.
- `POST /api/v1/rj-line` — Dynamic on-air time announcements, banter, and jokes.
- `POST /api/v1/synthesize` — Direct multilingual 44.1 kHz MP3 broadcast voice synthesis.
- `POST /api/v1/chat-reply` — Listener message comprehension, 18+ filtering, and multilingual voice generation in listener's native script.
- `POST /api/v1/process-message` — Firestore-backed message pipeline updating `status: "replied"` and `replyAudioBase64`.
- `POST /api/v1/flash-news` — Instant 3-part breaking news bulletins with intro alert and smooth outro.

### Security & Authentication
- **Firebase ID Token Verification**: All `/api/v1/*` endpoints are protected via FastAPI dependency `Depends(get_current_user)`.
- **JWT Cryptographic Verification**: Each request requires `Authorization: Bearer <token>`, cryptographically validated via Firebase Admin SDK (`auth.verify_id_token`).
- **Client Integration**:
  - **Android**: `AgentApiClient.kt` acquires fresh token via `FirebaseAuth.getInstance().currentUser.getIdToken(false).await()` on every request.
  - **Web**: `App.tsx` acquires token via `auth.currentUser?.getIdToken()` and includes Bearer header.
- Unauthenticated requests are rejected with `HTTP 401 Unauthorized`.
- `/health` and root routes remain public for Cloud Run container liveness probes.

### AI Voice Personality & Speed Synchronization (On-Air Chat Replies)
- **Multilingual Voice Styles**: Configured regional Neural2, Wavenet, and Studio voices across languages for `"male"` (e.g. `ta-IN-Wavenet-B`, `en-US-Neural2-D`), `"female"` (e.g. `ta-IN-Wavenet-A`, `en-US-Neural2-F`), and `"studio"` (e.g. `ta-IN-Wavenet-D`, `en-US-Studio-Q`).
- **Prompt Persona Customization**: `gemini.py` adapts its persona instruction and broadcast delivery style based on the broadcaster's voice selection (female RJ, male RJ, studio host) and supports custom station tone.
- **Dynamic Speed Factor**: Speech rate from `RjConfig` (Relaxed: 0.90x, Normal: 1.00x, Fast: 1.12x) is dynamically persisted to Firestore station documents and applied to Cloud TTS synthesis.
- **Real-Time Synchronization**: `AiStudioScreen.kt` and `SettingsScreen.kt` immediately persist voice personality and speech speed to `stations/{uid}` and `users/{uid}` in Firestore upon segmented pill changes.
- **Client & Web Forwarding**: `ChatOnAirManager.kt` and `App.tsx` pass `voice` and `speakingRate` to `/api/v1/process-message`, with Cloud Run reading station document settings as automatic fallback.
- **Token Budget Protection**: Increased Gemini 2.5 Flash `max_output_tokens` to `4096` to accommodate internal chain-of-thought reasoning tokens and prevent multiline poem/story truncation.

### AI Full Takeover (Auto-Pilot) Mode
- **Autonomous Station Operation**: Master toggle in `AiStudioScreen.kt` allowing the AI Co-Host to fully run live broadcasts 24/7 without manual broadcaster intervention.
- **Customizable News Interval**: Broadcasters can customize news bulletin frequency (`5m`, `10m` [default], `15m`, `20m`, `30m`). When active, `RjScheduler` automatically pulls unbroadcast Firestore news stories, voices them via Gemini + TTS, and plays or queues them.
- **Customizable RJ Time Check Interval**: Configurable interval (`10m`, `15m`, `30m` [default], `45m`, `60m`) where the AI RJ announces the projected time, now-playing track, and upcoming track, with built-in validation preventing time check overlaps near track endings.
- **News Delivery Styles**: Broadcasters can toggle between **Play Now** (interrupts music and automatically resumes the interrupted track from the saved position) or **Add to Up Next** (enqueues sequentially).
- **Continuous Playlist Replay (Auto-Replay)**: Loop toggle (default ON) that automatically restarts the playlist from index 0 when the last track ends, guaranteeing zero dead air.
### Intelligent Listener Song Requests & Dynamic On-Air Play Next
- **Dual-Case Song Request Resolution**:
  - When listeners send song requests in live chat (in English, Tamil, Sinhala, etc.), Gemini 2.5 Flash analyzes the message against the station's active library tracks (`playlist_tracks`).
  - **Song Found in Library**: The AI RJ crafts an on-air shoutout greeting the listener, confirming the song was found, and dedicating the track. The engine enqueues the RJ announcement at the end of the current song, and automatically promotes the requested music track to play immediately next (`Playlist.moveTrackToPlayNext`).
  - **Song NOT in Library**: The AI RJ responds with genuine warmth, thanking the listener by name, politely explaining that the song is not in the station library right now, and enthusiastically promising to add it tomorrow.
- **Multilingual Dedication Prompts**: Dynamic prompt rules in `gemini.py` handle natural language requests in English and regional languages (e.g. Tamil: *"உங்க request '...' பாட்டு நம் station library-ல இருக்கு! உடனே play ஆகுது!"* vs *"இப்போ library-ல இல்ல, ஆனா நாளைக்கு கண்டிப்பா add பண்றேன்!"*).
- **Library Synchronization**: `StationManager.syncPlaylist()` syncs active music titles and artists to Firestore `stations/{uid}` both on broadcaster login and upon going live.
- **Seamless Broadcast Flow**: Audio clips and music tracks are synchronized in `ChatOnAirManager.kt` and `Playlist.kt` so listener dedications play out smoothly with zero dead air.

---

## 9. Remaining work

- Optional: Add custom station banner image upload for broadcasters.
- Prepare demo video and competition presentation deck for AI Builder Cup 2026.

