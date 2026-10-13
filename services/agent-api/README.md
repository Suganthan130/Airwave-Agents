# agent-api

Backend service for Airwave Agents — the Gemini-powered co-host brain.
Runs on Cloud Run and exposes the API consumed by the web and Android clients.
Python source lives under `src/airwave_agents/` (api, core, services, models); tests under `tests/`.
Keep secrets out of this folder — load them from the environment at runtime.
