"""FastAPI endpoints for Airwave Agents API."""

import logging
from fastapi import APIRouter, Depends, HTTPException
from airwave_agents.core.auth import get_current_user, get_optional_user
from airwave_agents.core.config import resolve_voice, resolve_multilingual_voice
from airwave_agents.models.requests import (
    RjLineRequest,
    SynthesizeRequest,
    ChatReplyRequest,
    ProcessMessageRequest,
    FlashNewsRequest,
)
from airwave_agents.models.responses import (
    RjLineResponse,
    SynthesizeResponse,
    ChatReplyResponse,
    ProcessMessageResponse,
)
from airwave_agents.services.gemini import generate_rj_text, generate_rj_chat_reply
from airwave_agents.services.tts import synthesize_mp3
from airwave_agents.services.chat import process_one_message

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1",
    tags=["Airwave Agents"],
)


@router.post("/rj-line", response_model=RjLineResponse, dependencies=[Depends(get_current_user)])
async def create_rj_line(req: RjLineRequest):
    """Generates an RJ line (time check, banter, joke, greeting, or flash news) and synthesizes audio."""
    ctx = {
        "nowTrack": req.nowTrack or "",
        "nowArtist": req.nowArtist or "",
        "nextTrack": req.nextTrack or "",
        "nextArtist": req.nextArtist or "",
        "timeLabel": req.timeLabel or "",
        "headline": req.headline or "",
        "content": req.content or "",
    }
    voice = resolve_voice(req.voice)
    speaking_rate = float(req.speakingRate or 1.0)
    is_news = req.type in ("news", "flash_news")

    try:
        text = await generate_rj_text(req.type, ctx)
        audio_b64 = await synthesize_mp3(
            text=text,
            voice=voice,
            speaking_rate=speaking_rate,
        )
        return RjLineResponse(
            text=text,
            audioBase64=audio_b64,
            mime="audio/mpeg",
        )
    except Exception as e:
        logger.error(f"Error in rj-line: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"RJ generation failed: {str(e)}")


@router.post("/synthesize", response_model=SynthesizeResponse, dependencies=[Depends(get_current_user)])
async def synthesize_text(req: SynthesizeRequest):
    """Directly synthesizes text to 44.1 kHz broadcast MP3."""
    clean_text = req.text.strip()[:1000]
    if not clean_text:
        raise HTTPException(status_code=400, detail="Text is required.")

    voice = resolve_voice(req.voice)
    speaking_rate = float(req.speakingRate or 1.0)

    try:
        audio_b64 = await synthesize_mp3(
            text=clean_text,
            voice=voice,
            speaking_rate=speaking_rate,
            language_code=req.languageCode,
        )
        return SynthesizeResponse(
            text=clean_text,
            audioBase64=audio_b64,
            mime="audio/mpeg",
        )
    except Exception as e:
        logger.error(f"Error in synthesize: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"TTS synthesis failed: {str(e)}")


@router.post("/chat-reply", response_model=ChatReplyResponse, dependencies=[Depends(get_current_user)])
async def create_chat_reply(req: ChatReplyRequest):
    """Analyzes a listener chat message, checks 18+ filter, detects language, and generates voice reply."""
    clean_msg = req.messageText.strip()[:500]
    if not clean_msg:
        raise HTTPException(status_code=400, detail="Message text is required.")

    effective_voice = req.voice or "male"
    effective_rate = float(req.speakingRate or 1.0)
    effective_persona = req.aiPersona or ""

    try:
        result = await generate_rj_chat_reply(
            user_name=req.userName or "Listener",
            user_country=req.userCountry or "Global",
            message_text=clean_msg,
            now_track=req.nowTrack or "",
            now_artist=req.nowArtist or "",
            next_track=req.nextTrack or "",
            next_artist=req.nextArtist or "",
            voice_style=effective_voice,
            ai_persona=effective_persona,
        )

        if result.get("skipped"):
            return ChatReplyResponse(
                allowed=False,
                skipped=True,
                replyText="",
                replyAudioBase64=None,
                languageCode="en-US",
                languageName="English",
            )

        reply_text = result.get("text", "")
        language_code = result.get("languageCode", "en-US")
        language_name = result.get("languageName", "English")

        voice_config = resolve_multilingual_voice(
            language_code=language_code,
            text=reply_text,
            voice_style=effective_voice,
        )
        audio_b64 = await synthesize_mp3(
            text=reply_text,
            voice=voice_config["voice"],
            speaking_rate=effective_rate,
            language_code=voice_config["lang"],
        )

        return ChatReplyResponse(
            allowed=True,
            skipped=False,
            replyText=reply_text,
            replyAudioBase64=audio_b64,
            languageCode=voice_config["lang"],
            languageName=language_name,
            mime="audio/mpeg",
        )
    except Exception as e:
        logger.error(f"Error in chat-reply: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Chat reply failed: {str(e)}")


@router.post("/process-message", response_model=ProcessMessageResponse, dependencies=[Depends(get_optional_user)])
async def process_message(req: ProcessMessageRequest):
    """Processes a queued chat message document in Firestore, generating and saving the reply."""
    try:
        result = await process_one_message(
            message_id=req.messageId,
            now_track=req.nowTrack or "",
            now_artist=req.nowArtist or "",
            next_track=req.nextTrack or "",
            next_artist=req.nextArtist or "",
            voice=req.voice,
            speaking_rate=req.speakingRate,
            ai_persona=req.aiPersona,
        )
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])


        return ProcessMessageResponse(
            messageId=req.messageId,
            status=result.get("status", "unknown"),
            replyText=result.get("replyText"),
            replyAudioBase64=result.get("replyAudioBase64"),
            languageCode=result.get("languageCode"),
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in process-message: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Processing failed: {str(e)}")


@router.post("/flash-news", response_model=RjLineResponse, dependencies=[Depends(get_current_user)])
async def create_flash_news(req: FlashNewsRequest):
    """Creates an immediate breaking flash news radio bulletin."""
    ctx = {
        "headline": req.headline.strip(),
        "content": (req.content or "").strip(),
    }
    voice = resolve_voice(req.voice)
    speaking_rate = float(req.speakingRate or 1.0)

    try:
        text = await generate_rj_text("flash_news", ctx)
        audio_b64 = await synthesize_mp3(
            text=text,
            voice=voice,
            speaking_rate=speaking_rate,
        )
        return RjLineResponse(
            text=text,
            audioBase64=audio_b64,
            mime="audio/mpeg",
        )
    except Exception as e:
        logger.error(f"Error in flash-news: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Flash news creation failed: {str(e)}")
