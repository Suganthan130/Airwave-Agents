"""Pydantic models for incoming API requests."""

from typing import Optional
from pydantic import BaseModel, Field


class RjLineRequest(BaseModel):
    type: str = Field(default="joke", description="Type of line: time, flash_news, joke, station_id, intro, greeting")
    nowTrack: Optional[str] = Field(default="", description="Currently playing song title")
    nowArtist: Optional[str] = Field(default="", description="Currently playing artist")
    nextTrack: Optional[str] = Field(default="", description="Upcoming song title in queue")
    nextArtist: Optional[str] = Field(default="", description="Upcoming artist in queue")
    timeLabel: Optional[str] = Field(default="", description="Formatted time string, e.g. '7:45 PM'")
    headline: Optional[str] = Field(default="", description="News headline for flash news")
    content: Optional[str] = Field(default="", description="News body text for flash news")
    voice: Optional[str] = Field(default=None, description="Voice ID or style ('male', 'female', 'studio')")
    speakingRate: Optional[float] = Field(default=1.0, ge=0.5, le=2.0, description="Speed factor")


class SynthesizeRequest(BaseModel):
    text: str = Field(..., description="Text to synthesize to speech")
    voice: Optional[str] = Field(default=None, description="Cloud TTS voice name or style")
    speakingRate: Optional[float] = Field(default=1.0, ge=0.5, le=2.0)
    languageCode: Optional[str] = Field(default=None, description="Optional BCP-47 language tag, e.g. 'ta-IN'")


class ChatReplyRequest(BaseModel):
    userName: Optional[str] = Field(default="Listener", description="Name of the listener")
    userCountry: Optional[str] = Field(default="Global", description="Country/location of the listener")
    messageText: str = Field(..., description="Listener chat message text")
    nowTrack: Optional[str] = Field(default="", description="Currently playing track")
    nowArtist: Optional[str] = Field(default="", description="Currently playing artist")
    nextTrack: Optional[str] = Field(default="", description="Next track up in queue")
    nextArtist: Optional[str] = Field(default="", description="Next artist up in queue")
    voice: Optional[str] = Field(default=None, description="Broadcaster voice style: 'male', 'female', or 'studio'")
    speakingRate: Optional[float] = Field(default=1.0, ge=0.5, le=2.0, description="Speech rate factor (e.g. 0.9, 1.0, 1.12)")
    aiPersona: Optional[str] = Field(default=None, description="Optional custom station AI persona or tone")


class ProcessMessageRequest(BaseModel):
    messageId: str = Field(..., description="Firestore document ID in 'messages' collection")
    nowTrack: Optional[str] = Field(default="", description="Currently playing track")
    nowArtist: Optional[str] = Field(default="", description="Currently playing artist")
    nextTrack: Optional[str] = Field(default="", description="Next track up")
    nextArtist: Optional[str] = Field(default="", description="Next artist up")
    voice: Optional[str] = Field(default=None, description="Broadcaster voice style: 'male', 'female', or 'studio'")
    speakingRate: Optional[float] = Field(default=None, ge=0.5, le=2.0, description="Speech rate factor")
    aiPersona: Optional[str] = Field(default=None, description="Optional custom station AI persona or tone")



class FlashNewsRequest(BaseModel):
    headline: str = Field(..., description="Breaking news story headline")
    content: Optional[str] = Field(default="", description="Story details / background")
    voice: Optional[str] = Field(default=None, description="Voice ID or style")
    speakingRate: Optional[float] = Field(default=1.0, ge=0.5, le=2.0)
