# Airwave Radio — Features & System Testing Guide

A comprehensive guide for testing and verifying all the core capabilities of **Airwave Radio**.

---

## 1. AI Full Takeover Mode (Auto-Pilot 24/7)

The AI Co-Host can autonomously manage the station without human intervention.

### How to Configure & Test:
1. Open the **AI** tab (AI Studio).
2. Toggle **AI Full Takeover** to **ON**.
3. Customize the parameters:
   - **News Bulletin Interval**: Select `5m`, `10m` (default), `15m`, `20m`, or `30m`.
   - **News Delivery Style**: Choose **Play Now** (interrupts music and automatically resumes the interrupted track from the saved position) or **Add to Up Next** (enqueues news bulletin sequentially).
   - **RJ Time Check Interval**: Select `10m`, `15m`, `30m` (default), `45m`, or `60m`.
   - **Continuous Auto-Replay**: Keep toggled ON to automatically loop the playlist back to track 1 when the last song finishes.
4. Go back to the **Broadcast** tab. Notice the green **AI Auto-Pilot Active** status banner.
5. Watch `RjScheduler` autonomously pull unbroadcast news from Firestore, generate bulletins with Gemini + Cloud TTS, and handle time checks at the configured intervals.

---

## 2. Breaking News Alerts (1-Minute Interval Pop-Up)

When live on-air, the app monitors the Firestore `news` collection for new alerts.

### How to Test:
1. Ensure the station is broadcasting (**Go Live**).
2. Every 1 minute, the app queries Firestore for unbroadcast news stories.
3. When a story is retrieved, an alert dialog appears on the screen with:
   - **Headline** and **Summary**.
   - **Play Now (Interrupt)**: Immediately cuts over to the AI-voiced news bulletin and resumes the interrupted song when finished.
   - **Add to Up Next**: Voices the bulletin in the background and places it in the Up Next queue.
   - **Dismiss**: Marks the story as broadcasted and dismisses the dialog.

---

## 3. Dynamic On-Air Time Checks

Broadcasters can trigger on-air time announcements manually or through the scheduler.

### Validation & Progress:
- In the **Broadcast** tab, tap **Time Check**.
- The app calculates the exact time needed for voice generation.
- **Progress Time Check**: If there is insufficient time remaining before the current song ends (less than the minimum needed threshold), an error/warning is displayed to prevent awkward track collisions.
- When voiced, the AI RJ announces the projected clock time, the currently playing track, and the upcoming song.

---

## 4. Custom RJ Voice Persona & Speed

Broadcasters can customize the AI Co-Host persona and speech rate.

### Available Options:
- **Voice Style**:
  - `Female RJ` (e.g. `ta-IN-Wavenet-A` / `en-US-Neural2-F`)
  - `Male RJ` (e.g. `ta-IN-Wavenet-B` / `en-US-Neural2-D`)
  - `Studio Host` (e.g. `ta-IN-Wavenet-D` / `en-US-Studio-Q`)
- **Speaking Speed**:
  - `Relaxed (0.90x)` — Laid-back, late-night radio feel.
  - `Normal (1.00x)` — Standard upbeat radio broadcast.
  - `Fast (1.12x)` — High-energy morning show pace.
- **Persistence**:
  - Changes in the **AI** tab or **Settings** tab immediately save to local `SettingsStore` and sync to Firestore `stations/{uid}` so all Cloud Run voice synthesis reflects the broadcaster's preferences.

---

## 5. Pre-Loaded Music Library & On-Demand Loading

To avoid forcing demo audio on broadcasters who bring their own files:
- When the app is fresh, it prompts: *"Add Pre-Loaded Songs to Library"*.
- Tapping **Add Tracks** copies high-quality, royalty-free MP3 tracks from app assets to the local library.
- Broadcasters can also tap **Add Music** at any time to pick local audio files from device storage.

---

## 6. Station Metadata & Icecast Synchronization on Firestore

The transmitter coordinates live station details on Firestore:
- **Document**: `stations/{uid}`
- **Synced Properties**:
  - `serverHost`, `serverPort`, `serverMount`, `serverProtocol`: Icecast connection credentials.
  - `stationName`, `stationDescription`, `broadcasterName`: Display info.
  - `voice`, `speakingRate`: AI Co-Host settings.
  - `playlist`: Array of `{title, artist}` representing the broadcaster's loaded music.
- **Listener Web Client**:
  - Resolves `stations/{uid}` in real time to stream audio and connect to the live listener chat.
