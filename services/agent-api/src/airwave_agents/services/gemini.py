"""Gemini 2.5 Flash on Vertex AI for Radio Co-Host intelligence."""

import json
import logging
import re
import vertexai
from vertexai.generative_models import GenerativeModel, GenerationConfig
from airwave_agents.core.config import PROJECT_ID, LOCATION, detect_script_language

logger = logging.getLogger(__name__)

_vertex_initialized = False


def _ensure_vertex():
    global _vertex_initialized
    if not _vertex_initialized:
        try:
            vertexai.init(project=PROJECT_ID, location=LOCATION)
            _vertex_initialized = True
        except Exception as e:
            logger.warning(f"Vertex AI initialization warning: {e}")


async def generate_rj_text(type_name: str, ctx: dict) -> str:
    """Generates an RJ line, time check, or breaking flash news script with Gemini 2.5 Flash."""
    _ensure_vertex()
    is_news = type_name in ("news", "flash_news")

    persona = (
        "You are an authoritative, world-class radio news presenter on 'Airwave Agents' radio. "
        "Speak with the urgent charisma, journalistic clarity, and polished cadence of a top-tier live breaking news broadcast. "
        "Plain spoken broadcast text only — no emojis, no hashtags, no stage directions, no bracketed sound effects, no quotes, no asterisks."
        if is_news
        else
        "You are an upbeat, witty radio jockey (RJ) on 'Airwave Agents' radio. "
        "Speak naturally and with authentic on-air radio energy. Keep it to ONE or TWO short, punchy sentences. "
        "Plain spoken text only — no emojis, no hashtags, no stage directions, no sound effects, no quotes, no asterisks."
    )

    now = f'"{ctx.get("nowTrack")}" by {ctx.get("nowArtist") or "an artist"}' if ctx.get("nowTrack") else ""
    next_s = f'"{ctx.get("nextTrack")}" by {ctx.get("nextArtist") or "an artist"}' if ctx.get("nextTrack") else ""

    if is_news:
        headline = f"Headline: {ctx.get('headline')}\n" if ctx.get("headline") else ""
        content = f"Details: {ctx.get('content')}\n" if ctx.get("content") else ""
        task = (
            f"You are scripting a live on-air Flash News bulletin for radio listeners.\n\n"
            f"STORY:\n{headline}{content}\n"
            f"STRUCTURE REQUIREMENTS:\n"
            f"1. FLASH MUSIC & INTRO: Open with an urgent, authoritative on-air alert announcing the breaking news "
            f'(e.g. "This is an Airwave Flash News bulletin." or "Airwave Flash News: Breaking news update.") followed immediately by the headline.\n'
            f"2. DELIVER THE NEWS: Deliver the core facts with journalistic urgency, authority, and crystal clarity in 2 concise sentences.\n"
            f"3. SMOOTH OUTRO: End smoothly with a polished, authentic broadcast sign-off that eases the listener seamlessly back into the station's music "
            f'(e.g. "We will bring you more updates as this story unfolds. Now, heading smoothly back to the music on Airwave Agents." or '
            f'"Stay tuned to Airwave Agents for developing details. Now, back to your music."). The ending must transition smoothly, calmly, and naturally back to the music.\n\n'
            f"Keep the entire spoken bulletin to 3-4 punchy sentences total. Plain spoken broadcast text only — do not include sound effect labels in brackets, stage directions, or markdown symbols."
        )
    elif type_name in ("time", "time_check"):
        time_str = ctx.get("timeLabel") or "now"
        task = (
            f"Give an upbeat on-air time check. The current time is {time_str}. "
            f"Deliver it naturally with the charisma of an authentic live radio host."
            + (f" Mention that we just jammed to {now}." if now else "")
            + (f" Tease that up next we've got {next_s}." if next_s else "")
        )
    elif type_name == "intro":
        task = (
            f"Introduce the next song coming up: {next_s}." + (f" The one that just played was {now}." if now else "")
            if next_s
            else "Tease that great music is coming up next."
        )
    elif type_name == "joke":
        task = "Tell one short, clean, funny radio one-liner or joke."
    elif type_name == "station_id":
        task = "Give a short, punchy station ID for 'Airwave Agents' radio."
    elif type_name == "greeting":
        time_str = f" It is around {ctx.get('timeLabel')}." if ctx.get("timeLabel") else ""
        task = f"Warmly greet the listeners.{time_str}"
    else:
        task = "Say a short, friendly on-air line."

    try:
        model = GenerativeModel("gemini-2.5-flash")
        config = GenerationConfig(
            max_output_tokens=2048,
            temperature=0.7 if is_news else 0.9,
        )
        response = model.generate_content(
            f"{persona}\n\nTask: {task}",
            generation_config=config,
        )
        text = response.text.strip() if response and response.text else ""
        if text:
            # Clean quotes and asterisks
            return re.sub(r'["#*]', "", text)[:1000]
    except Exception as e:
        logger.warning(f"Gemini generate_rj_text error: {e}. Using fallback.")

    # Fallbacks
    if is_news:
        hl = ctx.get("headline", "")
        if hl:
            return f"This is an Airwave Flash News bulletin. {hl}. We will bring you more updates as this story unfolds. Now, heading smoothly back to the music on Airwave Agents."
        return "This is an Airwave Flash News bulletin. Stay tuned for developing updates. Now, heading smoothly back to the music on Airwave Agents."

    if type_name in ("time", "time_check"):
        t = ctx.get("timeLabel") or "now"
        if next_s:
            return f"Airwave Agents time check: it's {t}. Don't touch that dial, coming right up is {next_s}!"
        return f"You're locked into Airwave Agents. The time is now {t}. Let's keep the music rolling!"

    return "You are tuned in to Airwave Agents radio, the best music right here."


async def generate_rj_chat_reply(
    user_name: str,
    user_country: str,
    message_text: str,
    now_track: str = "",
    now_artist: str = "",
    next_track: str = "",
    next_artist: str = "",
    voice_style: str = "male",
    ai_persona: str = "",
) -> dict:
    """Multilingual listener chat analysis, 18+ filter, and personalized RJ reply tailored to broadcaster AI persona."""
    _ensure_vertex()

    now_info = f'- Currently Playing Song: "{now_track}" by {now_artist}\n' if now_track else ""
    next_info = f'- Next Song in Queue: "{next_track}" by {next_artist}\n' if next_track else ""

    norm_style = str(voice_style or "female").lower().strip()
    if norm_style in ("1", "female", "fem", "woman", "chirp", "chirp-hd", "en-us-chirp-hd-f"):
        rj_persona = (
            "You are an upbeat, charismatic female on-air radio jockey (RJ) on 'Airwave Agents' radio. "
            "Speak with radiant, warm female radio presenter energy and natural charisma."
        )
    elif norm_style in ("2", "studio", "pro", "studio_pro", "en-us-studio-o"):
        rj_persona = (
            "You are a top-tier, authoritative studio radio host and broadcaster on 'Airwave Agents' radio. "
            "Speak with sophisticated, polished FM radio delivery and effortless studio charm."
        )
    elif norm_style in ("3", "tamil", "tamil_host"):
        rj_persona = (
            "You are a warm, charismatic Tamil radio jockey (RJ) on 'Airwave Agents' radio. "
            "Speak with vibrant, engaging radio presenter energy and authentic Tamil broadcast enthusiasm."
        )
    else:
        rj_persona = (
            "You are an upbeat, charismatic on-air radio jockey (RJ) on 'Airwave Agents' radio. "
            "Speak with vibrant, engaging radio presenter energy and authentic broadcast enthusiasm."
        )

    custom_persona_clause = f"\nCUSTOM STATION TONE & AI PERSONA: {ai_persona}\n" if ai_persona else ""

    prompt = (
        f"{rj_persona}\n{custom_persona_clause}\n"
        f"A live listener has sent an on-air chat message into your broadcast studio:\n"
        f"- Listener Name: {user_name or 'Listener'}\n"
        f"- Country: {user_country or 'Global'}\n"
        f'- Listener Message: "{message_text}"\n'
        f"{now_info}{next_info}\n"

        f"CRITICAL BROADCAST & LANGUAGE RULES:\n"
        f"1. 18+ & INAPPROPRIATE FILTER: If the message asks an unusually inappropriate, sexually explicit, 18+, adult, crude, abusive, offensive, or harmful query, output \"SKIP\".\n"
        f"2. PRIMARY LANGUAGES SUPPORTED (TAMIL & ENGLISH):\n"
        f"   - If Tamil script OR romanized Tamil (Tanglish) or Tamil request -> reply in authentic, natural, charismatic spoken TAMIL (using Tamil script for flawless TTS speech fluency, with languageCode 'ta-IN' and languageName 'Tamil')!\n"
        f"   - For all other messages/requests -> reply in charismatic, engaging ENGLISH (with languageCode 'en-US' and languageName 'English')!\n"
        f"3. DELIVER CREATIVE REQUESTS IMMEDIATELY ON-AIR (NEVER POSTPONE TO 'NEXT TIME'):\n"
        f"   - If the listener asks for a poem (kavithai / கவிதை / shayari / poem), a short story (கதை / kahani / story), a joke, a riddle, a proverb, thoughts on rain/love/friendship/life, or ANY creative request:\n"
        f"     * RECITE OR TELL IT RIGHT NOW in this on-air reply!\n"
        f"     * NEVER say 'I will say it next time', 'adutha murai solren', 'next break la solren', 'next time I will tell you', 'stay tuned later', or postpone their request.\n"
        f"     * Compose a beautiful, heartwarming, rhyming poem or short story right here on the spot and deliver it on-air with soulful, charismatic energy!\n"
        f"4. ANSWER WHAT THE LISTENER SPECIFICALLY ASKED — NEVER GIVE A GENERIC TEMPLATE:\n"
        f"   - Directly and specifically answer the exact question or comment the listener asked.\n"
        f"   - Greet them warmly by name and country, then deliver the answer / poem / story immediately.\n"
        f"   - 2 to 4 natural, conversational, punchy radio sentences.\n"
        f"   - Plain spoken broadcast text only — do not include sound effect labels in brackets, emojis, hashtags, or asterisks.\n"
        f"5. Return JSON ONLY with this exact schema:\n"
        f"{{\n"
        f'  "skipped": false,\n'
        f'  "languageCode": "ta-IN",\n'
        f'  "languageName": "Tamil",\n'
        f'  "replyText": "spoken reply text here in listener\'s language answering their specific question and delivering any requested poem/story immediately"\n'
        f"}}"
    )

    try:
        model = GenerativeModel("gemini-2.5-flash")
        config = GenerationConfig(
            max_output_tokens=4096,
            temperature=0.7,
            response_mime_type="application/json",
        )
        response = model.generate_content(prompt, generation_config=config)
        raw = response.text.strip() if response and response.text else ""

        logger.info(f"Gemini chat raw response: {raw[:200]}...")
        parsed = None
        try:
            parsed = json.loads(raw, strict=False)
        except Exception:
            clean_raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw.strip(), flags=re.MULTILINE)
            try:
                parsed = json.loads(clean_raw, strict=False)
            except Exception:
                match = re.search(r"\{[\s\S]*\}", raw)
                if match:
                    try:
                        parsed = json.loads(match.group(0), strict=False)
                    except Exception:
                        pass

        if parsed:
            is_skipped = parsed.get("skipped") is True or str(parsed.get("replyText", "")).strip().upper() == "SKIP"
            if is_skipped:
                return {"skipped": True, "text": "", "languageCode": "en-US", "languageName": "English"}

            reply = re.sub(r'["#*]', "", str(parsed.get("replyText", "")))[:1000].strip()
            if reply:
                return {
                    "skipped": False,
                    "text": reply,
                    "languageCode": parsed.get("languageCode") or "en-US",
                    "languageName": parsed.get("languageName") or "English",
                }

        # Resilient regex fallback for multiline poems/stories inside replyText JSON
        reply_match = re.search(r'"replyText"\s*:\s*"([\s\S]*?)(?:"\s*\}|",\s*"\w+":)', raw)
        if reply_match:
            reply = re.sub(r'["#*]', "", reply_match.group(1)).replace("\\n", "\n").replace('\\"', '"')[:1000].strip()
            if reply and reply.upper() != "SKIP":
                script = detect_script_language(reply) or "en-US"
                return {
                    "skipped": False,
                    "text": reply,
                    "languageCode": script,
                    "languageName": "Detected",
                }

        if raw.strip().upper() == "SKIP":
            return {"skipped": True, "text": "", "languageCode": "en-US", "languageName": "English"}

        clean = re.sub(r'["#*]', "", raw)[:1000].strip()
        script = detect_script_language(clean) or "en-US"
        return {
            "skipped": False,
            "text": clean,
            "languageCode": script,
            "languageName": "Detected",
        }

    except Exception as e:
        logger.warning(f"Gemini generate_rj_chat_reply error: {e}. Using language-aware fallback.")

    # Multilingual Fallbacks
    script = detect_script_language(message_text)
    is_tamil = script == "ta-IN" or bool(re.search(r"tamil|pesuvingala|saputingala|erukingala|vanakkam|sugam", message_text, re.I))

    if is_tamil:
        tamil_msg = f"வணக்கம் {user_name or 'நண்பரே'}! ஆமாங்க, நான் கண்டிப்பா தமிழ் பேசுவேன்! "
        if next_track:
            tamil_msg += f"அடுத்ததா உங்களுக்காக {next_track} பாடல் ஒலிக்கப்போகிறது, கேட்டு மகிழுங்கள்!"
        else:
            tamil_msg += "உங்களுக்காக அருமையான பாடல்கள் தொடர்ந்து ஏர்வேவ் ஏஜென்ட்ஸ்ல வந்துகிட்டே இருக்கு!"
        return {
            "skipped": False,
            "text": tamil_msg,
            "languageCode": "ta-IN",
            "languageName": "Tamil",
        }

    if script == "hi-IN" or bool(re.search(r"hindi|namaste|kaisa", message_text, re.I)):
        return {
            "skipped": False,
            "text": f"नमस्ते {user_name or 'दोस्त'}! आपके प्यारे संदेश के लिए बहुत धन्यवाद, मैं बिल्कुल ठीक हूँ! Airwave Agents पर शानदार संगीत सुनते रहिए!",
            "languageCode": "hi-IN",
            "languageName": "Hindi",
        }

    return {
        "skipped": False,
        "text": f"Hello {user_name or 'friend'} from {user_country or 'around the world'}! Thanks so much for reaching out, we're doing great and loving the music with you on Airwave Agents!",
        "languageCode": "en-US",
        "languageName": "English",
    }
