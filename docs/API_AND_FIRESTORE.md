# Airwave Agents — API & Firestore Reference

Complete structure of the backend REST API (`services/agent-api/`) and the Cloud
Firestore data model shared by the Android transmitter and the web listener app.

- **Backend:** FastAPI on Google Cloud Run (`gemini-2.5-flash` + Google Cloud TTS)
- **Base URL:** `https://agent-api-191983702527.us-central1.run.app`
- **API prefix:** `/api/v1`
- **Project:** `airwave-agents` (number `191983702527`), region `us-central1`
- **Clients:** Android transmitter (broadcaster), Web app (listener)

---

## 1. Architecture at a glance

```
┌──────────────────┐        writes/reads         ┌──────────────────┐
│  Web listener     │  ───────────────────────▶   │                  │
│  (apps/web)       │   messages / stations        │  Cloud Firestore │
└───────┬──────────┘                               │  (default db)    │
        │ POST /chat-reply, /news-feed             │  messages        │
        ▼                                          │  stations        │
┌──────────────────┐   POST /flash-news, /rj-line  │  users           │
│  agent-api        │  ◀──────────────────────────  │  news (legacy)   │
│  (Cloud Run)      │   reads station/user/message  └──────────────────┘
│  Gemini + TTS     │   writes replies to messages           ▲
└───────┬──────────┘                                         │
        │ scrapes                                            │ reads/writes
        ▼                                            ┌───────┴──────────┐
┌──────────────────┐                                 │ Android transmit │
│  Hiru News (HTML) │                                 │ (apps/android)   │
└──────────────────┘                                 └──────────────────┘
```

---

## 2. Authentication

All `/api/v1/*` endpoints use **Firebase ID tokens** via the `Authorization` header:

```
Authorization: Bearer <firebase_id_token>
```

| Dependency | Behavior |
| --- | --- |
| `get_current_user` | **Required.** Missing/invalid/expired token → `401 Unauthorized`. |
| `get_optional_user` | **Optional.** Verifies the token if present, otherwise proceeds as anonymous (no `401`). |

Health endpoints (`/`, `/health`) need no auth.

---

## 3. REST API endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET`  | `/` | none | Service metadata / health |
| `GET`  | `/health` | none | Liveness probe → `{"status":"ok"}` |
| `POST` | `/api/v1/rj-line` | required | Generate an RJ line (time check, joke, intro, flash news…) + audio |
| `POST` | `/api/v1/synthesize` | required | Text → broadcast-quality MP3 (TTS only) |
| `POST` | `/api/v1/chat-reply` | required | Analyze a listener message, 18+ filter, detect language, voice reply |
| `POST` | `/api/v1/process-message` | optional | Process a queued `messages` doc end-to-end and write the reply back |
| `POST` | `/api/v1/flash-news` | required | Turn a headline + body into a breaking-news radio bulletin + audio |
| `POST` | `/api/v1/news-feed` | optional | Fetch the latest real **Hiru News** stories (scraped server-side) |

All request/response bodies are JSON. Audio is returned as **base64-encoded MP3**
(`audio/mpeg`, 44.1 kHz).

---

### 3.1 `POST /api/v1/rj-line`

Generates a spoken RJ line and synthesizes it.

**Request**

| Field | Type | Req | Default | Notes |
| --- | --- | --- | --- | --- |
| `type` | string | – | `"joke"` | `time`, `flash_news`, `joke`, `station_id`, `intro`, `greeting` |
| `nowTrack` | string | – | `""` | Currently playing title |
| `nowArtist` | string | – | `""` | Currently playing artist |
| `nextTrack` | string | – | `""` | Next title in queue |
| `nextArtist` | string | – | `""` | Next artist in queue |
| `timeLabel` | string | – | `""` | e.g. `"7:45 PM"` (for `type=time`) |
| `headline` | string | – | `""` | For `type=flash_news` |
| `content` | string | – | `""` | For `type=flash_news` |
| `voice` | string | – | `null` | `male` / `female` / `studio` or a Cloud TTS voice id |
| `speakingRate` | float | – | `1.0` | `0.5`–`2.0` |

**Response** (`RjLineResponse`)

```json
{ "text": "…spoken line…", "audioBase64": "<mp3>", "mime": "audio/mpeg", "languageCode": "en-US" }
```

---

### 3.2 `POST /api/v1/synthesize`

Direct text-to-speech. `text` is trimmed to 1000 chars; empty → `400`.

**Request**

| Field | Type | Req | Default | Notes |
| --- | --- | --- | --- | --- |
| `text` | string | ✅ | – | Text to speak |
| `voice` | string | – | `null` | Style or Cloud TTS voice id |
| `speakingRate` | float | – | `1.0` | `0.5`–`2.0` |
| `languageCode` | string | – | `null` | BCP-47, e.g. `ta-IN` |

**Response** (`SynthesizeResponse`): `{ "text", "audioBase64", "mime" }`

---

### 3.3 `POST /api/v1/chat-reply`

Generates an on-air voice reply to a listener message. Runs an 18+/safety filter
and auto-detects language (English / Tamil / etc.). `messageText` trimmed to 500 chars.

**Request**

| Field | Type | Req | Default |
| --- | --- | --- | --- |
| `userName` | string | – | `"Listener"` |
| `userCountry` | string | – | `"Global"` |
| `messageText` | string | ✅ | – |
| `nowTrack` / `nowArtist` | string | – | `""` |
| `nextTrack` / `nextArtist` | string | – | `""` |
| `voice` | string | – | `null` |
| `speakingRate` | float | – | `1.0` |
| `aiPersona` | string | – | `null` |

**Response** (`ChatReplyResponse`)

```json
{
  "allowed": true, "skipped": false,
  "replyText": "…", "replyAudioBase64": "<mp3|null>",
  "languageCode": "en-US", "languageName": "English", "mime": "audio/mpeg"
}
```

A filtered message returns `allowed:false, skipped:true` with no audio.

---

### 3.4 `POST /api/v1/process-message`

Picks up a **queued** `messages` document, generates the reply, and writes it back
to Firestore (status → `processing` → `replied`/`skipped`). Idempotent: a doc not
in `queued` state is returned unchanged.

**Request**

| Field | Type | Req | Notes |
| --- | --- | --- | --- |
| `messageId` | string | ✅ | Firestore doc id in `messages` |
| `nowTrack` / `nowArtist` | string | – | Overrides; otherwise read from station/doc |
| `nextTrack` / `nextArtist` | string | – | — |
| `voice` | string | – | — |
| `speakingRate` | float | – | — |
| `aiPersona` | string | – | — |
| `playlist` | array\<object\> | – | `[{ "title", "artist" }]` for song-request matching |

**Response** (`ProcessMessageResponse`)

```json
{
  "messageId": "…", "status": "replied",
  "replyText": "…", "replyAudioBase64": "<mp3|null>", "languageCode": "en-US",
  "isSongRequest": false, "songFound": false, "requestedSongTitle": ""
}
```

---

### 3.5 `POST /api/v1/flash-news`

**Request**

| Field | Type | Req | Default |
| --- | --- | --- | --- |
| `headline` | string | ✅ | – |
| `content` | string | – | `""` |
| `voice` | string | – | `null` |
| `speakingRate` | float | – | `1.0` |

**Response** (`RjLineResponse`): `{ "text", "audioBase64", "mime", "languageCode" }`

---

### 3.6 `POST /api/v1/news-feed`

Fetches real Sri Lankan news scraped from **Hiru News** (Local, International,
Sports, Business, Entertainment), interleaved and de-duplicated. Hiru exposes no
RSS, so the backend parses the public HTML listing pages.

**Request**

| Field | Type | Req | Default | Notes |
| --- | --- | --- | --- | --- |
| `limit` | int | – | `24` | `1`–`60` |

**Response** (`NewsFeedResponse`)

```json
{
  "source": "Hiru News",
  "count": 24,
  "items": [
    {
      "id": "hiru_494630",
      "title": "Rosamund Pike highlights vital mine clearance work in Sri Lanka",
      "description": "Oscar-nominated actress …",
      "category": "Local",
      "published": "Saturday, 17 October 2026 - 9:49",
      "order": 1,
      "link": "https://hirunews.lk/goldfmnews/494630/…"
    }
  ]
}
```

Returns `502` if no stories could be fetched.

---

### 3.7 Error format

All errors use FastAPI's standard shape:

```json
{ "detail": "Human-readable message" }
```

| Code | Meaning |
| --- | --- |
| `400` | Bad request (empty required text) |
| `401` | Missing/invalid/expired Firebase token |
| `500` | Generation/TTS failure |
| `502` | Upstream fetch (Hiru) failed / empty |

---

## 4. Firestore data model

Database: **`(default)`**, native mode. Active collections: `messages`, `stations`,
`users`. `news` is **legacy / deprecated** (news now comes from `/news-feed`).

### 4.1 `messages` — listener chats & AI replies

One doc per listener message. Written by the web app (and Android for broadcaster
chats); the reply fields are written back by the backend (`process-message` /
`chat-reply`). Document id is auto-generated.

| Field | Type | Written by | Description |
| --- | --- | --- | --- |
| `stationId` | string | client | Target station id, or `"global"` |
| `userId` | string | client | Firebase uid of sender |
| `userName` | string | client | Display name |
| `userCountry` | string | client | Country / `"Global"` |
| `userType` | string | client | `broadcaster` \| `listener` |
| `userAvatar` | string\|null | client | Photo URL |
| `text` | string | client | Message body |
| `status` | string | client → backend | `queued` → `processing` → `replied` \| `skipped` |
| `createdAt` | timestamp | client | `serverTimestamp()` |
| `replyText` | string | backend | AI reply text |
| `replyAudioBase64` | string | backend | Base64 MP3 of the reply |
| `replyLanguage` | string | backend | e.g. `English`, `Tamil` |
| `replyLanguageCode` | string | backend | BCP-47, e.g. `en-US` |
| `replyVoice` | string | backend | Resolved Cloud TTS voice id |
| `replyVoiceStyle` | string | backend | `male` \| `female` \| `studio` |
| `replySpeakingRate` | number | backend | Rate used |
| `replyCreatedAt` | timestamp | backend | `SERVER_TIMESTAMP` |
| `isSongRequest` | bool | backend | Detected song request |
| `songFound` | bool | backend | Request matched a playlist track |
| `requestedSongTitle` | string | backend | Parsed requested title |

**Status lifecycle**

```
queued ──(process-message)──▶ processing ──▶ replied   (text + audio ready)
                                          └─▶ skipped   (18+/safety filter)
```

Listeners are read with `orderBy("createdAt","desc").limit(50)` (shown ascending).

### 4.2 `stations` — broadcaster stations

One doc per broadcaster station; `stationId` is typically the broadcaster's uid.
Written by the Android transmitter, read by the web app and the backend.

| Field | Type | Description |
| --- | --- | --- |
| `stationId` | string | Station id (doc id) |
| `stationTitle` | string | Public station name |
| `broadcasterName` | string | Host display name |
| `broadcasterEmail` | string | Host email |
| `streamUrl` | string | Icecast/Shoutcast stream URL |
| `publicPageUrl` | string | Listener page URL |
| `host` / `port` / `mount` / `serverType` | string | Stream server details |
| `isLive` | bool | **Whether the station is currently on air** |
| `nowPlayingTrack` | string | Current track title |
| `nowPlayingArtist` | string | Current track artist |
| `listenersCount` | number | Live listener count |
| `voice` | string | RJ voice style |
| `speakingRate` | number | RJ speed |
| `aiPersona` | string | Station AI tone/persona |
| `playlist` | array | Track list `[{ title, artist, … }]` |
| `updatedAt` | number/timestamp | Last update |

> `isLive` gates the web chat: listeners can only send messages while the station
> is on air (see `apps/web/src/components/ChatBox.tsx`).

**Legacy aliases** still tolerated on read: `broadcasterId`, `uniqueId`,
`publicListeningUrl`, `rjVoice`, `rjSpeed`, `nowTrack`, `nowArtist`,
`nextPlayingTrack`, `nextPlayingArtist`, `stationTone`.

### 4.3 `users` — user profiles

One doc per user; doc id is the Firebase uid.

| Field | Type | Description |
| --- | --- | --- |
| `uid` | string | Firebase uid |
| `displayName` | string | Name |
| `email` | string\|null | Email |
| `photoURL` | string\|null | Avatar |
| `country` | string | Country |
| `userType` | string | `broadcaster` \| `listener` |
| `stationId` | string | Linked station (broadcasters) |
| `lastChatAt` | number | Last message timestamp (rate-limit) |
| `createdAt` | number/timestamp | Account creation |
| `settings` | map | Optional per-user station settings (fallback for backend) |

### 4.4 `news` *(legacy / deprecated)*

Formerly held curated bulletins. News is now served live from `/news-feed`
(Hiru). The collection is unused and left empty; do not seed it unless restoring a
curated-override feature. Historic shape: `{ title, description, category,
published, order }`.

---

## 5. AI model (Gemini)

| Item | Value |
| --- | --- |
| Model | **`gemini-2.5-flash`** (env `GEMINI_MODEL`, default in `core/config.py`) |
| Platform | **Vertex AI** (`vertexai.init(project=PROJECT_ID, location=LOCATION)`) |
| Region | `us-central1` (`GOOGLE_CLOUD_LOCATION`) |
| SDK | `google-cloud-aiplatform` 1.76.0 (`vertexai.generative_models`) |
| Context / output | 1,048,576 in · 65,536 out |
| Pricing | ~$0.50 / 1M input · ~$3.00 / 1M output tokens |
| Where | `services/gemini.py` → `generate_rj_text`, `generate_rj_chat_reply` |

> Default model is `gemini-2.5-flash` with automatic fallback to `gemini-2.5-pro`. The
> model id is read from the `GEMINI_MODEL` env var, so it can be retuned or rolled
> back without a code change.

**Generation configs**

| Use | `max_output_tokens` | `temperature` | Output |
| --- | --- | --- | --- |
| RJ line / flash news (`generate_rj_text`) | 2048 | `0.7` news · `0.9` banter | plain text |
| Chat reply (`generate_rj_chat_reply`) | 4096 | `0.7` | **JSON** (`response_mime_type="application/json"`) |

The chat-reply call returns structured JSON (reply text, safety/skip flag, detected
language, song-request detection). Vertex AI is initialized once (lazy) per process.

---

## 6. Google Cloud APIs & SDKs

| API / service | Python SDK (version) | Used for | Code |
| --- | --- | --- | --- |
| **Vertex AI** (Generative AI) | `google-cloud-aiplatform` 1.76.0 | Gemini 2.5 Flash text generation | `services/gemini.py` |
| **Cloud Text-to-Speech** | `google-cloud-texttospeech` 2.23.0 | Voice synthesis (MP3) | `services/tts.py` |
| **Cloud Firestore** (Admin) | `firebase-admin` 6.6.0 (`firestore`) | Read/write `messages`, `stations`, `users` | `core/firebase.py`, `services/chat.py` |
| **Firebase Auth** (Admin) | `firebase-admin` 6.6.0 (`auth`) | Verify client ID tokens | `core/auth.py` |
| **Cloud Run** | — (deploy target) | Hosts the FastAPI service | `Dockerfile` |

**Client-side (apps):** Firebase JS/Android SDKs — **Firebase Authentication**
(Google + email/password sign-in) and **Cloud Firestore** (realtime listeners on
`messages` / `stations`). The Android transmitter streams to **Icecast/Shoutcast**
(not a Google API). News is scraped from Hiru News over HTTP via `httpx` (also not
a Google API).

**GCP APIs to enable** on project `airwave-agents`: Vertex AI API
(`aiplatform.googleapis.com`), Cloud Text-to-Speech API (`texttospeech.googleapis.com`),
Cloud Firestore API (`firestore.googleapis.com`), Cloud Run API, Cloud Build API
(for `--source` deploys). **Auth:** Application Default Credentials via the runtime
service account `191983702527-compute@developer.gserviceaccount.com` — it needs
Vertex AI User, Cloud TTS, and Firestore access. No API keys live on the clients.

---

## 7. Voice / TTS models

| Item | Value |
| --- | --- |
| Engine | Google **Cloud Text-to-Speech** |
| Default RJ voice | **`en-US-Chirp3-HD-Leda`** (`RJ_VOICE`) |
| Audio encoding | **MP3**, `sample_rate_hertz = 44100`, `pitch = 0.0` |
| `speakingRate` | API accepts `0.5`–`2.0`; TTS clamps to **`0.7`–`1.5`** |
| Request range (`/api/v1`) | `speakingRate` validated `0.5`–`2.0` (default `1.0`) |

**Supported voices** (`core/config.py` → `MULTILINGUAL_VOICES` / `VOICE_MAP`)

| Language | Style | Cloud TTS voice | Model family |
| --- | --- | --- | --- |
| English (`en-US`) | female / host *(default)* | `en-US-Chirp3-HD-Leda` | Chirp 3 HD |
| English (`en-US`) | studio / male | `en-US-Studio-O` | Studio |
| English (`en-US`) | alt female | `en-US-Chirp-HD-F` | Chirp HD |
| Tamil (`ta-IN`) | female / host *(default)* | `ta-IN-Chirp3-HD-Leda` | Chirp 3 HD |
| Tamil (`ta-IN`) | male | `ta-IN-Wavenet-B` | WaveNet |
| Tamil (`ta-IN`) | studio | `ta-IN-Wavenet-D` | WaveNet |
| Tamil (`ta-IN`) | alt | `ta-IN-Wavenet-A` | WaveNet |

**Voice-style aliases** accepted in `voice`: `male`, `female`, `studio`,
`chirp`/`chirp3`/`chirp-hd`/`leda`, `host`, `studio_pro`, `tamil`/`tamil_female`/
`tamil_male`/`tamil_studio`, numeric `0` (Leda), `1` (Studio-O), `2` (Tamil Leda),
`3` (Tamil Wavenet-A), or a full Cloud TTS voice id (e.g. `en-US-Studio-O`).

**Language selection** (`resolve_multilingual_voice`)

1. **Script detection** — Tamil Unicode range `U+0B80–U+0BFF` in the text → `ta-IN`.
2. Else an explicit `languageCode`/style starting with `ta` → Tamil.
3. Otherwise English (`en-US`). *(Other languages fall back to English/Tamil for now.)*
4. An explicit hyphenated voice id matching the target language is used as-is.

**3-tier TTS fallback** (`synthesize_mp3`) for robustness:

```
1) resolved regional voice (e.g. ta-IN-Chirp3-HD-Leda)
      └─ fail ─▶ 2) language default (language_code only, no voice name)
                      └─ fail ─▶ 3) en-US + RJ_VOICE (en-US-Chirp3-HD-Leda)
```

---

## 8. Quick examples

```bash
# Health
curl -s https://agent-api-191983702527.us-central1.run.app/health

# News feed (no auth required)
curl -s -X POST \
  https://agent-api-191983702527.us-central1.run.app/api/v1/news-feed \
  -H "Content-Type: application/json" -d '{"limit":24}'

# Flash news (auth required)
curl -s -X POST \
  https://agent-api-191983702527.us-central1.run.app/api/v1/flash-news \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"headline":"Headline here","content":"Body…","voice":"female","speakingRate":1.0}'
```

---

*Source of truth: `services/agent-api/src/airwave_agents/` (api/router.py,
models/requests.py, models/responses.py, services/) and the Firestore reads/writes
in `apps/android/transmiter/` and `apps/web/src/`. Update this file when those change.*
