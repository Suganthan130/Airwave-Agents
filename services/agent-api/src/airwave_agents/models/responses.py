"""Pydantic models for API responses."""

from typing import Optional
from pydantic import BaseModel


class RjLineResponse(BaseModel):
    text: str
    audioBase64: str
    mime: str = "audio/mpeg"
    languageCode: Optional[str] = "en-US"


class SynthesizeResponse(BaseModel):
    text: str
    audioBase64: str
    mime: str = "audio/mpeg"


class ChatReplyResponse(BaseModel):
    allowed: bool = True
    skipped: bool = False
    replyText: str = ""
    replyAudioBase64: Optional[str] = None
    languageCode: str = "en-US"
    languageName: str = "English"
    mime: str = "audio/mpeg"


class ProcessMessageResponse(BaseModel):
    messageId: str
    status: str
    replyText: Optional[str] = None
    replyAudioBase64: Optional[str] = None
    languageCode: Optional[str] = None
