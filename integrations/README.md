# integrations

Client integrations that connect to the Airwave Agents platform.

- `transmiter/` — the Android **transmitter** app (Kotlin + C++). Broadcasters use it to push
  the live stream, with the Gemini co-host mixed in, to Icecast/Shoutcast. Public — lives in this repo.
- `core-app/` — **private git submodule** → the existing core transmitter app
  ([`BroadcastMySelf-Radio-Tool`](https://github.com/Suganthan130/BroadcastMySelf-Radio-Tool), private).

## Privacy

Only a pointer to `core-app/` is committed here (`.gitmodules` + a commit reference) — never its
source. This repo can go public while the core app stays private; fetching it requires access to
the private repo. Clone with `git clone --recurse-submodules <url>` (or `git submodule update
--init`). Do not copy the private core app's source into this repo.
