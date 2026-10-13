# transmiter

The Airwave Agents **transmitter** — the Android app broadcasters run to push the live stream
(with the Gemini co-host mixed in) to Icecast/Shoutcast. Standard Android Gradle project
(Kotlin, with a C++/NDK streaming layer under `app/`).

The private core transmitter app lives alongside this folder as the `core-app/` submodule
(see [`../README.md`](../README.md)). Keep broadcaster secrets — keystores, `local.properties`,
`google-services.json` — out of version control; the local `.gitignore` already excludes them.
