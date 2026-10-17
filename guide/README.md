# Airwave Agents — Guides & Documentation

Welcome to the **Airwave Agents** guide directory! This collection of documents provides example chat prompts, architecture walkthroughs, and step-by-step testing workflows for broadcasters and reviewers.

---

## Guide Index

| Guide | Description |
|---|---|
| 📜 [**Example Chats & Test Prompts**](EXAMPLE_CHATS.md) | Ready-to-use sample listener chat messages in English and Tamil for testing song requests (in library and not in library), time inquiries, celebrations/birthdays, banter, and safety filters. |
| 🎵 [**Song Requests & Dynamic Play Next Guide**](SONG_REQUESTS_GUIDE.md) | In-depth architectural breakdown and step-by-step testing instructions for the Gemini-powered listener song request feature. |
| 🛠️ [**Features & System Testing Guide**](FEATURES_TESTING_GUIDE.md) | Testing instructions for AI Full Takeover (Auto-Pilot), real-time Breaking News pop-ups (live Hiru News feed), Dynamic Time Checks, RJ Voice customization, and Firestore synchronization. |
| 🔐 [**Authentication Guide**](AUTHENTICATION_GUIDE.md) | Ready-to-use demo broadcaster and listener accounts for evaluating the transmitter and web listener portal. |

---

## Demo Accounts

Ready-to-use demo accounts for evaluation. The **broadcaster** account runs the
transmitter (Android app & web); the **listener** account tunes in from the browser.

👉 **All demo credentials are in the [Authentication Guide](AUTHENTICATION_GUIDE.md)** —
the single source of truth for the test emails and passwords.

**Demo station references (not secret):**
- **Station UID:** `BtA8T6KYxuZf8bxFNKleilheS9v1`
- **Public listener page:** https://airwave-agents.web.app/?station=BtA8T6KYxuZf8bxFNKleilheS9v1

---

## Quick Reference: Testing Song Requests

### 1. Song IN Library (Promote to Play Next)
> *"Can you please play Across the Oceans next? Loving the vibe today!"*
- **Result**: AI RJ announces on-air shoutout dedicating the song to the listener, and **Across the Oceans** is automatically moved to Up Next right after the announcement, decorated with an **`AI MOVED`** badge.

### 2. Song NOT in Library (Polite Tomorrow Promise)
> *"Please play Shape of You by Ed Sheeran!"*
- **Result**: AI RJ thanks the listener warmly on-air, explains the song isn't in the station library right now, and promises enthusiastically to add it tomorrow.

---

## Supported Pre-loaded Songs in Library
- **Across the Oceans**
- **Athirapalli**
- **Dancing in the Stars**
- **Eternal Light**
- **Summer Adventures**
- **Summer Vibes**
- **Original Song**
- **Vocal Song 1 / 2 / 3**
