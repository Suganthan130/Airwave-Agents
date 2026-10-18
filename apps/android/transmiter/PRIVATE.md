# Private Components

Some parts of this app are **intentionally kept local and not published** to this repository.
They hold proprietary logic, so they are listed in the project's `.gitignore` and never
committed. The rest of the transmitter (UI screens, data, services) is public.

## What's kept private — and why

| Path | Why it's ignored |
|---|---|
| `app/src/main/java/lk/sugaapps/airwave/PrivateExperience.kt` | Private experience logic — proprietary behavior that is kept local, not published. |
| `app/src/main/cpp/` | The native broadcast engine (proprietary C++ / JNI). |
| `app/src/main/java/lk/sugastreamapps/` | The private audio streaming engine (`AudioStreamer`, `RealBroadcastEngine`). |

## Notes

- Because these files are gitignored, they exist only on local machines. A fresh clone will be
  missing them, so the app is built locally with the private pieces in place.
- Do not remove these entries from `.gitignore`, and do not copy this private logic into any
  public file — keeping it out of the repo is deliberate.
