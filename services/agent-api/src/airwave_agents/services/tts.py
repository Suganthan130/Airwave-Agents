"""Google Cloud Text-to-Speech synthesis service."""

import base64
import logging
from google.cloud import texttospeech
from airwave_agents.core.config import (
    RJ_VOICE,
    resolve_multilingual_voice,
    resolve_voice,
)

logger = logging.getLogger(__name__)

_tts_client = None


def get_tts_client():
    global _tts_client
    if _tts_client is None:
        _tts_client = texttospeech.TextToSpeechClient()
    return _tts_client


async def synthesize_mp3(
    text: str,
    voice: str | None = None,
    speaking_rate: float = 1.0,
    language_code: str | None = None,
) -> str:
    """Synthesizes text to 44.1 kHz MP3, returning Base64 string."""
    client = get_tts_client()
    rate = max(0.7, min(1.5, float(speaking_rate or 1.0)))

    voice_config = resolve_multilingual_voice(
        language_code=language_code,
        text=text,
        voice_style=voice,
    )
    lang = voice_config["lang"]
    resolved_voice = voice_config["voice"]

    synthesis_input = texttospeech.SynthesisInput(text=text)
    audio_config = texttospeech.AudioConfig(
        audio_encoding=texttospeech.AudioEncoding.MP3,
        sample_rate_hertz=44100,
        speaking_rate=rate,
        pitch=0.0,
    )

    # First attempt: With resolved regional voice
    try:
        voice_params = texttospeech.VoiceSelectionParams(
            language_code=lang,
            name=resolved_voice,
        )
        response = client.synthesize_speech(
            input=synthesis_input,
            voice=voice_params,
            audio_config=audio_config,
        )
        return base64.b64encode(response.audio_content).decode("utf-8")
    except Exception as e:
        logger.warning(f"TTS with voice {resolved_voice} failed: {e}. Trying language default {lang}...")

    # Second attempt: With language code only
    try:
        voice_params = texttospeech.VoiceSelectionParams(language_code=lang)
        response = client.synthesize_speech(
            input=synthesis_input,
            voice=voice_params,
            audio_config=audio_config,
        )
        return base64.b64encode(response.audio_content).decode("utf-8")
    except Exception as e2:
        logger.warning(f"TTS with language default {lang} failed: {e2}. Falling back to {RJ_VOICE}...")

    # Third attempt: Fallback to en-US standard RJ voice
    voice_params = texttospeech.VoiceSelectionParams(
        language_code="en-US",
        name=RJ_VOICE,
    )
    response = client.synthesize_speech(
        input=synthesis_input,
        voice=voice_params,
        audio_config=audio_config,
    )
    return base64.b64encode(response.audio_content).decode("utf-8")
