"""Listener chat processing service with Firestore sync."""

import asyncio
import logging
from firebase_admin import firestore
from airwave_agents.core.firebase import get_firestore
from airwave_agents.core.config import resolve_multilingual_voice
from airwave_agents.services.gemini import generate_rj_chat_reply
from airwave_agents.services.tts import synthesize_mp3

logger = logging.getLogger(__name__)


async def process_one_message(
    message_id: str,
    now_track: str = "",
    now_artist: str = "",
    next_track: str = "",
    next_artist: str = "",
    voice: str | None = None,
    speaking_rate: float | None = None,
    ai_persona: str | None = None,
) -> dict:
    """Processes a single queued listener chat message document in Firestore with retry resilience,
    applying broadcaster voice personality, speech speed, and custom AI persona.
    """
    db = get_firestore()
    if not db:
        return {"error": "Firestore not initialized"}

    doc_ref = db.collection("messages").document(message_id)

    # Retry up to 6 times (with 500ms delay) to tolerate client-server Firestore replication latency
    doc_snap = None
    for attempt in range(6):
        doc_snap = doc_ref.get()
        if doc_snap and doc_snap.exists:
            break
        await asyncio.sleep(0.5)

    if not doc_snap or not doc_snap.exists:
        logger.warning(f"Message document {message_id} not found in Firestore after 6 attempts.")
        return {"error": f"Message document {message_id} not found"}

    current = doc_snap.to_dict() or {}
    status = current.get("status")
    if status in ("replied", "skipped"):
        return {"status": status, "messageId": message_id}

    # Mark processing in Firestore
    try:
        doc_ref.update({"status": "processing"})
    except Exception as e:
        logger.warning(f"Could not mark status processing on {message_id}: {e}")

    try:
        # Load broadcaster station profile & AI settings
        station_id = current.get("stationId")
        station_data: dict = {}
        if station_id and station_id != "global":
            try:
                station_snap = db.collection("stations").document(station_id).get()
                if station_snap.exists:
                    station_data = station_snap.to_dict() or {}
                else:
                    # Fallback to users/{uid} settings if station doc not indexed
                    user_snap = db.collection("users").document(station_id).get()
                    if user_snap.exists:
                        u_dict = user_snap.to_dict() or {}
                        station_data = u_dict.get("settings", {}) or u_dict
            except Exception as e:
                logger.warning(f"Could not fetch station settings for {station_id}: {e}")

        # Fill track info from station if missing
        if not now_track:
            now_track = station_data.get("nowPlayingTrack") or station_data.get("nowTrack", "")
            now_artist = station_data.get("nowPlayingArtist") or station_data.get("nowArtist", "")
        if not next_track:
            next_track = station_data.get("nextPlayingTrack") or station_data.get("nextTrack", "")
            next_artist = station_data.get("nextPlayingArtist") or station_data.get("nextArtist", "")

        # 1. Resolve Voice Personality Setting (male, female, studio)
        effective_voice = voice
        if not effective_voice:
            st_voice = station_data.get("voice") or station_data.get("rjVoice")
            if st_voice is not None:
                if str(st_voice) in ("1", "female"):
                    effective_voice = "female"
                elif str(st_voice) in ("2", "studio"):
                    effective_voice = "studio"
                elif str(st_voice) in ("0", "male"):
                    effective_voice = "male"
                else:
                    effective_voice = str(st_voice)
            else:
                effective_voice = "male"

        # 2. Resolve Voice Speech Speed Setting (relaxed: 0.90, normal: 1.00, fast: 1.12)
        effective_rate = speaking_rate
        if effective_rate is None:
            st_rate = station_data.get("speakingRate")
            st_speed = station_data.get("rjSpeed")
            if st_rate is not None:
                try:
                    effective_rate = float(st_rate)
                except (ValueError, TypeError):
                    effective_rate = 1.0
            elif st_speed is not None:
                try:
                    idx = int(st_speed)
                    if idx == 0:
                        effective_rate = 0.90
                    elif idx == 2:
                        effective_rate = 1.12
                    else:
                        effective_rate = 1.00
                except (ValueError, TypeError):
                    effective_rate = 1.0
            else:
                effective_rate = 1.0

        # 3. Resolve Custom AI Persona / Tone
        effective_persona = ai_persona or station_data.get("aiPersona") or station_data.get("stationTone", "")

        logger.info(
            f"Processing chat {message_id}: station={station_id}, voice={effective_voice}, rate={effective_rate}, persona={effective_persona or 'default'}"
        )

        # Generate personalized multilingual RJ reply with 18+ filter
        result = await generate_rj_chat_reply(
            user_name=current.get("userName", "Listener"),
            user_country=current.get("userCountry", "Global"),
            message_text=current.get("text", ""),
            now_track=now_track or current.get("nowTrack", ""),
            now_artist=now_artist or current.get("nowArtist", ""),
            next_track=next_track or current.get("nextTrack", ""),
            next_artist=next_artist or current.get("nextArtist", ""),
            voice_style=effective_voice,
            ai_persona=effective_persona,
        )

        if result.get("skipped"):
            doc_ref.update({
                "status": "skipped",
                "replyText": "",
                "replyCreatedAt": firestore.SERVER_TIMESTAMP,
            })
            return {"status": "skipped", "messageId": message_id}

        reply_text = result.get("text", "")
        language_code = result.get("languageCode", "en-US")
        language_name = result.get("languageName", "English")

        # Resolve matching regional neural voice with broadcaster's voice preference
        voice_config = resolve_multilingual_voice(
            language_code=language_code,
            text=reply_text,
            voice_style=effective_voice,
        )

        # Synthesize broadcast-quality MP3 (44.1 kHz) in listener's native voice with broadcaster's speed
        audio_base64 = await synthesize_mp3(
            text=reply_text,
            voice=voice_config["voice"],
            speaking_rate=effective_rate,
            language_code=voice_config["lang"],
        )

        doc_ref.update({
            "status": "replied",
            "replyText": reply_text,
            "replyLanguage": language_name or voice_config["lang"],
            "replyLanguageCode": voice_config["lang"],
            "replyVoice": voice_config["voice"],
            "replyVoiceStyle": effective_voice,
            "replySpeakingRate": effective_rate,
            "replyAudioBase64": audio_base64,
            "replyCreatedAt": firestore.SERVER_TIMESTAMP,
        })

        return {
            "status": "replied",
            "messageId": message_id,
            "replyText": reply_text,
            "replyAudioBase64": audio_base64,
            "languageCode": voice_config["lang"],
            "voice": voice_config["voice"],
            "speakingRate": effective_rate,
        }
    except Exception as e:
        logger.error(f"Error processing message {message_id}: {e}", exc_info=True)
        # Attempt to mark error in document so client does not stay stuck in processing
        runCatching = None
        try:
            doc_ref.update({"status": "error", "error": str(e)})
        except Exception:
            pass
        return {"error": f"Failed to process message: {str(e)}"}
