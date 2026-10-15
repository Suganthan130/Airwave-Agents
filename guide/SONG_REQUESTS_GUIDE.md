# Song Request & Dynamic Play Next — Guide & Architecture

This guide explains how the **Intelligent Song Request** feature works across **Airwave Agents** (`agent-api`, `apps/web`, and Android `apps/android/transmiter`).

---

## Architecture Overview

```
[ Listener Web Chat ]
        │  (Sends message: "Can you play Across the Oceans please?")
        ▼
[ Firestore: stations/{stationId}/chat/{messageId} ]
        │  (Status: "queued")
        ▼
[ Agent API (Cloud Run) /process-message ]
   ├── Gemini 2.5 Flash Prompt analyzes message against station's active playlist
   │    ├── Checks isSongRequest
   │    ├── Checks songFound in playlist_tracks
   │    └── Generates custom on-air dedication script (or polite promise)
   └── Cloud Text-to-Speech synthesizes 44.1 kHz broadcast MP3
        │
        ▼  (Status: "replied", replyAudioBase64, isSongRequest, songFound, requestedSongTitle)
[ Android Transmitter: ChatOnAirManager ]
   ├── Listener receives notification in real time via Firestore snapshot listener
   ├── Case 1 (Song Found):
   │     1. Enqueues AI voice dedication clip at end of current track
   │     2. Moves requested song in Playlist to play immediately next (Up Next)
   └── Case 2 (Song NOT Found):
         1. Enqueues polite AI reply clip at end of current track (promising tomorrow)
```

---

## Detailed Playback Flow

### Case A: Song Is In The Station Library
1. **Detection & Verification**:
   - Listener asks: `"Can you play Dancing in the Stars please?"`.
   - `gemini.py` receives the list of station tracks synced from `Playlist.musicTracks` (via `StationManager.syncPlaylist`).
   - Gemini detects `isSongRequest = true`, `songFound = true`, and sets `requestedSongTitle = "Dancing in the Stars"`.
   - Gemini drafts an enthusiastic on-air shoutout greeting the listener by name and announcing that their requested song is coming up next.
2. **Audio Generation**:
   - Cloud TTS voices the shoutout at the broadcaster's chosen speed and persona.
3. **Queueing & Sequence**:
   - `ChatOnAirManager.tryEnqueueForEndOfSong` downloads the synthesized MP3 and creates a `Playlist.Item`.
   - `Playlist.enqueueAtEndOfCurrentSong(shoutoutItem)` places the RJ voiceover immediately after the currently playing song.
   - `Playlist.moveTrackToPlayNext(matchedTrack)` searches the library using `findTrackByTitleOrQuery()`, tags the item with `isAiMoved = true`, and repositions the song right behind the shoutout.
   - **`AI MOVED` UI Badge**: The track is decorated with a distinct blue **`AI MOVED`** badge in the **Up Next** playlist, **Now Playing** banner, and **Queue Action Dialog**, providing clear visual feedback that the AI Co-Host re-ordered the track in response to a listener request.
4. **On-Air Cutover**:
   - When the current song finishes:
     1. The **AI RJ Shoutout** plays first (e.g. *"Here's Dancing in the Stars for Michael! Enjoy the music!"*).
     2. When the shoutout finishes, the playlist auto-advances seamlessly into **Dancing in the Stars** (showing the **`AI MOVED`** badge on air)!

---

### Case B: Song Is NOT In The Station Library
1. **Detection & Response**:
   - Listener asks: `"Please play Shape of You by Ed Sheeran!"`.
   - Gemini checks against the active tracks and determines that `"Shape of You"` is not currently in the playlist.
   - Sets `isSongRequest = true`, `songFound = false`.
   - Gemini generates a warm, courteous response:
     > *"Hey Sarah, thanks so much for tuning in! 'Shape of You' isn't in our station library right now, but I promise I'll add it tomorrow just for you! Stay tuned for more great tunes on Airwave Radio!"*
2. **On-Air Delivery**:
   - Synthesized and queued at the end of the song (`ChatOnAirManager`).
   - Broadcaster and listener hear the polite on-air reply at track transition without disrupting the playlist order.

---

## Key Code Components

| Component | File Path | Role |
|---|---|---|
| **AI Prompt & Request Detection** | [`gemini.py`](file:///Volumes/android/AI%20Builder%20Cup%202026/Airwave%20Agents/airwave-agents/services/agent-api/src/airwave_agents/services/gemini.py) | Rule 3 prompts for song request identification and dedication drafting. |
| **Playlist Fuzzy Matcher** | [`Playlist.kt`](file:///Volumes/android/AI%20Builder%20Cup%202026/Airwave%20Agents/airwave-agents/apps/android/transmiter/app/src/main/java/lk/sugaapps/airwave/engine/Playlist.kt) | `findTrackByTitleOrQuery()` strips conversational phrases and matches titles. |
| **Up Next Re-order Engine** | [`Playlist.kt`](file:///Volumes/android/AI%20Builder%20Cup%202026/Airwave%20Agents/airwave-agents/apps/android/transmiter/app/src/main/java/lk/sugaapps/airwave/engine/Playlist.kt) | `moveTrackToPlayNext()` shifts the requested song ahead of other queued tracks. |
| **On-Air Dispatcher** | [`ChatOnAirManager.kt`](file:///Volumes/android/AI%20Builder%20Cup%202026/Airwave%20Agents/airwave-agents/apps/android/transmiter/app/src/main/java/lk/sugaapps/airwave/data/ChatOnAirManager.kt) | Coordinates Firestore chat listener, audio caching, and toast notifications. |
| **Library Synchronizer** | [`StationManager.kt`](file:///Volumes/android/AI%20Builder%20Cup%202026/Airwave%20Agents/airwave-agents/apps/android/transmiter/app/src/main/java/lk/sugaapps/airwave/data/StationManager.kt) | Syncs active playlist tracks to `stations/{uid}` document in Firestore. |

---

## Step-by-Step Testing Procedure

1. **Launch Android App**: Open Airwave Radio on the broadcaster phone / emulator.
2. **Load Tracks**: In the **Tracks** tab, ensure you have music loaded (or tap the pre-load prompt).
3. **Go Live**: On the **Broadcast** tab, tap **Go Live**. The app automatically syncs the playlist to Firestore `stations/{uid}`.
4. **Open Listener Web App**:
   - Open your browser to `https://airwave-agents.web.app/station/<your_uid>` (or use the station URL copied from the app).
5. **Send a Test Request**:
   - Type in chat: `Can you please play Across the Oceans next?`
   - Observe in the Android app: Toast notification `"🎵 Song Request: 'Across the Oceans' moved to Up Next for ...!"`.
   - In the playlist, notice **Across the Oceans** is now positioned right after the current song and AI shoutout.
6. **Send an Unstocked Song Request**:
   - Type in chat: `Please play Despacito!`
   - Observe in the Android app: Toast notification `"💬 Song requested by ... not in library — RJ promised to add tomorrow!"`.
   - Listen to the polite AI dedication voiced seamlessly on-air at the track boundary.
