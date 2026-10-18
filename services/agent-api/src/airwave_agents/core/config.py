"""Configuration, voice mappings, and multilingual detection."""

import os
import re

PROJECT_ID = (
    os.getenv("GOOGLE_CLOUD_PROJECT")
    or os.getenv("GCLOUD_PROJECT")
    or "airwave-agents"
)
LOCATION = os.getenv("GOOGLE_CLOUD_LOCATION", "us-central1")

# Gemini LLM model (Vertex AI). Overridable via env for cost tuning / rollback.
# gemini-2.5-flash: high-speed, cost-efficient Flash tier with full Vertex AI regional availability.
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

RJ_VOICE = "en-US-Chirp3-HD-Leda"

VOICE_MAP = {
    # Main English voices (Chirp 3 HD Leda & Studio-O)
    "chirp": "en-US-Chirp3-HD-Leda",
    "chirp3": "en-US-Chirp3-HD-Leda",
    "chirp-hd": "en-US-Chirp3-HD-Leda",
    "leda": "en-US-Chirp3-HD-Leda",
    "en-us-chirp3-hd-leda": "en-US-Chirp3-HD-Leda",
    "en-us-chirp-hd-f": "en-US-Chirp-HD-F",
    "female": "en-US-Chirp3-HD-Leda",
    "host": "en-US-Chirp3-HD-Leda",
    "studio": "en-US-Studio-O",
    "studio_pro": "en-US-Studio-O",
    "en-us-studio-o": "en-US-Studio-O",
    "male": "en-US-Studio-O",
    "0": "en-US-Chirp3-HD-Leda",
    "1": "en-US-Studio-O",
    "2": "ta-IN-Chirp3-HD-Leda",
    "3": "ta-IN-Wavenet-A",
    # Main Tamil voices (Chirp 3 HD Leda + Wavenet)
    "tamil": "ta-IN-Chirp3-HD-Leda",
    "tamil_leda": "ta-IN-Chirp3-HD-Leda",
    "tamil_female": "ta-IN-Chirp3-HD-Leda",
    "tamil_male": "ta-IN-Wavenet-B",
    "tamil_studio": "ta-IN-Wavenet-D",
    "ta-in-chirp3-hd-leda": "ta-IN-Chirp3-HD-Leda",
    "ta-in-wavenet-a": "ta-IN-Wavenet-A",
    "ta-in-wavenet-b": "ta-IN-Wavenet-B",
    "ta-in-wavenet-d": "ta-IN-Wavenet-D",
}

MULTILINGUAL_VOICES = {
    # Main English voices (Chirp 3 HD Leda Female & Studio-O)
    "en": {
        "lang": "en-US",
        "female": "en-US-Chirp3-HD-Leda",
        "studio": "en-US-Studio-O",
        "male": "en-US-Studio-O",
        "voice": "en-US-Chirp3-HD-Leda",
    },
    "en-US": {
        "lang": "en-US",
        "female": "en-US-Chirp3-HD-Leda",
        "studio": "en-US-Studio-O",
        "male": "en-US-Studio-O",
        "voice": "en-US-Chirp3-HD-Leda",
    },

    # Main Tamil voices (Chirp 3 HD Leda Female, Wavenet-B Male, Wavenet-D Studio)
    "ta": {
        "lang": "ta-IN",
        "female": "ta-IN-Chirp3-HD-Leda",
        "male": "ta-IN-Wavenet-B",
        "studio": "ta-IN-Wavenet-D",
        "voice": "ta-IN-Chirp3-HD-Leda",
    },
    "ta-IN": {
        "lang": "ta-IN",
        "female": "ta-IN-Chirp3-HD-Leda",
        "male": "ta-IN-Wavenet-B",
        "studio": "ta-IN-Wavenet-D",
        "voice": "ta-IN-Chirp3-HD-Leda",
    },
    "ta-LK": {
        "lang": "ta-IN",
        "female": "ta-IN-Chirp3-HD-Leda",
        "male": "ta-IN-Wavenet-B",
        "studio": "ta-IN-Wavenet-D",
        "voice": "ta-IN-Chirp3-HD-Leda",
    },
}


def detect_script_language(text: str) -> str | None:
    """Detects regional language script using unicode ranges.
    Main regional language is Tamil. Other languages will be built in the future.
    """
    if not text:
        return None
    if re.search(r"[\u0B80-\u0BFF]", text):  # Tamil script
        return "ta-IN"
    return None


def resolve_multilingual_voice(
    language_code: str | None,
    text: str,
    voice_style: str | None = "chirp",
) -> dict:
    """Resolves matching TTS voice and language code based on text script or language code,
    respecting broadcaster's chosen voice personality ('chirp', 'studio', 'tamil').
    Main supported voices:
      - en-US-Chirp3-HD-Leda (English Female / Host)
      - en-US-Studio-O (English Studio Pro)
      - ta-IN-Chirp3-HD-Leda / ta-IN-Wavenet-A / B / D (Tamil Regional voices)
    Other languages fallback to English/Tamil until built in future.
    """
    script_lang = detect_script_language(text)

    is_tamil = (
        script_lang == "ta-IN"
        or (language_code and str(language_code).strip().lower().startswith("ta"))
        or str(voice_style or "").strip().lower() in ("tamil", "tamil_female", "tamil_male", "tamil_studio", "2", "3")
    )

    target_code = "ta-IN" if is_tamil else "en-US"
    entry = MULTILINGUAL_VOICES.get(target_code, MULTILINGUAL_VOICES["en-US"])

    # Normalize voice style
    raw_style = str(voice_style or "chirp").lower().strip()
    if raw_style in ("0", "chirp", "chirp3", "leda", "chirp-hd", "female", "fem", "woman", "en-us-chirp3-hd-leda", "en-us-chirp-hd-f"):
        style = "female"
    elif raw_style in ("1", "studio", "pro", "studio_pro", "en-us-studio-o"):
        style = "studio"
    elif raw_style in ("2", "3", "tamil", "tamil_female", "tamil_host", "ta-in-chirp3-hd-leda", "ta-in-wavenet-a"):
        style = "female"
    elif raw_style in ("tamil_male", "ta-in-wavenet-b", "male", "man"):
        style = "male"
    else:
        style = "female"

    # If caller passed an explicit Cloud TTS voice identifier that matches target language
    if voice_style and "-" in voice_style:
        clean_v = voice_style.strip()
        if is_tamil and clean_v.lower().startswith("ta-"):
            return {"lang": "ta-IN", "voice": clean_v, "style": style}
        if not is_tamil and clean_v.lower().startswith("en-"):
            return {"lang": "en-US", "voice": clean_v, "style": style}

    selected_voice = entry.get(style) or entry.get("voice") or entry.get("female")
    return {
        "lang": entry["lang"],
        "voice": selected_voice,
        "style": style,
    }


def resolve_voice(voice: str | None) -> str:
    """Maps friendly voice nicknames ('female', 'studio', 'male', '0', '1', '2', 'tamil') to Cloud TTS voice IDs."""
    if not voice:
        return RJ_VOICE
    clean = str(voice).lower().strip()
    return VOICE_MAP.get(clean, voice)

