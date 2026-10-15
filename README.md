# Airwave Agents

A Gemini-powered AI radio co-host.

## About the team

Airwave Agents are broadcasting technologists building on our own Icecast/Shoutcast
streaming app. We're creating a Gemini-powered AI co-host that helps radio stations
worldwide stay live 24/7, engaging listeners in their own language.

**Two clients:**

- **Android app — the transmitter.** Broadcasters use it to push the live stream
  (with the AI co-host mixed in) to Icecast/Shoutcast.
- **Web application — for listeners.** The audience tunes in from the browser.

## Technologies

- **Gemini** — AI co-host intelligence
- **Cloud Run** — hosting for the agent API
- **Firestore** — data storage
- **Firebase** — client services and hosting
- **Android (Kotlin + C++)** — streaming app integration
- **Icecast/Shoutcast** — audio streaming

## Folder structure

```
airwave-agents/
├── .github/
│   ├── workflows/
│   ├── ISSUE_TEMPLATE/
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
├── services/
│   └── agent-api/
│       ├── src/airwave_agents/
│       │   ├── api/
│       │   ├── core/
│       │   ├── services/
│       │   └── models/
│       └── tests/
├── apps/
│   ├── web/                   # listener web app
│   └── android/
│       ├── transmiter/        # Android transmitter app (public)
│       └── core-app/          # private submodule → BroadcastMySelf-Radio-Tool
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── planning/
│   └── assets/
├── README.md
├── CLAUDE.md
├── RULES.md
├── CONTRIBUTING.md
├── SECURITY.md
├── CHANGELOG.md
├── LICENSE
├── .gitignore
└── .editorconfig
```

## Getting started

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for how to contribute and [`RULES.md`](RULES.md)
for project conventions. Each main folder has its own README describing what belongs there.
