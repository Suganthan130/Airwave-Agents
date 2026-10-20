"""Hiru News feed scraper.

Fetches real Sri Lankan English news from Hiru's public Gold FM news pages so the
AI Co-Host News Studio broadcasts genuine headlines instead of fabricated copy.

Hiru exposes no RSS/JSON feed, so this parses the (very regular) HTML listing on
each category page with the standard library only — no extra dependency.
"""

import asyncio
import html as htmllib
import logging
import re

import httpx

from airwave_agents.core.firebase import get_firestore

logger = logging.getLogger(__name__)

HIRU_BASE = "https://hirunews.lk/goldfmnews"

# Firestore backup location: the last successful Hiru fetch, reused when a live
# fetch comes back empty (cold start, transient network error, or markup change).
_BACKUP_COLLECTION = "news_cache"
_BACKUP_DOC = "hiru_latest"

# Category listing page -> display label shown in the app.
CATEGORY_PAGES: dict[str, str] = {
    "local-news.php": "Local",
    "international-news.php": "International",
    "sports-news.php": "Sports",
    "business-news.php": "Business",
    "entertainment-news.php": "Entertainment",
}

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13) AirwaveAgents/1.0",
    "Accept": "text/html,application/xhtml+xml",
}

# One story block: title anchor -> timestamp -> summary text -> "Read More" link.
_STORY_RE = re.compile(
    r'<div class="all-section-tittle">\s*'
    r'<a href="(?P<link>[^"]+)"\s*>(?P<title>.*?)</a>\s*</div>'
    r'.*?<div class="middle-tittle-time">\s*(?P<time>.*?)\s*</div>'
    r'(?P<rest>.*?)<a href="[^"]*"\s*>Read More</a>',
    re.DOTALL,
)

_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")
_ID_RE = re.compile(r"/goldfmnews/(\d+)/")


def _clean(fragment: str) -> str:
    """Strips HTML tags, unescapes entities, and collapses whitespace."""
    return _WS_RE.sub(" ", htmllib.unescape(_TAG_RE.sub(" ", fragment))).strip()


def _parse_page(html: str, category: str) -> list[dict]:
    items: list[dict] = []
    for m in _STORY_RE.finditer(html):
        link = m.group("link").strip()
        title = _clean(m.group("title"))
        if not title or not link:
            continue
        id_match = _ID_RE.search(link)
        story_id = f"hiru_{id_match.group(1)}" if id_match else None
        if not story_id:
            continue
        items.append(
            {
                "id": story_id,
                "title": title,
                "description": _clean(m.group("rest")),
                "category": category,
                "published": _clean(m.group("time")),
                "link": link,
            }
        )
    return items


async def fetch_hiru_news(limit: int = 24) -> list[dict]:
    """Fetches and merges the latest stories across Hiru news categories.

    Categories are round-robin interleaved so the feed stays varied, then
    deduplicated by story id and capped at ``limit``. A failed category is
    skipped rather than failing the whole feed; the caller gets whatever
    categories responded.
    """
    async with httpx.AsyncClient(
        timeout=12.0, headers=_HEADERS, follow_redirects=True
    ) as client:

        async def grab(page: str, label: str) -> list[dict]:
            # Hiru is flaky on the first hit (cold start / transient timeout), so
            # retry a few times with a short backoff before giving up on a page.
            last_err: Exception | None = None
            for attempt in range(3):
                try:
                    resp = await client.get(f"{HIRU_BASE}/{page}")
                    resp.raise_for_status()
                    return _parse_page(resp.text, label)
                except Exception as e:  # network error, non-200, or markup change
                    last_err = e
                    if attempt < 2:
                        await asyncio.sleep(0.6 * (attempt + 1))
            logger.warning(f"Hiru fetch failed for {page} after 3 attempts: {last_err}")
            return []

        pools = await asyncio.gather(
            *[grab(page, label) for page, label in CATEGORY_PAGES.items()]
        )

    merged: list[dict] = []
    seen: set[str] = set()
    row = 0
    while any(row < len(pool) for pool in pools):
        for pool in pools:
            if row < len(pool):
                item = pool[row]
                if item["id"] not in seen:
                    seen.add(item["id"])
                    merged.append(item)
        row += 1

    merged = merged[:limit]
    for order, item in enumerate(merged, start=1):
        item["order"] = order
    return merged


def _save_backup(items: list[dict]) -> None:
    """Persists the latest good feed to Firestore so it can be served if a later
    fetch fails. Best-effort: a Firestore error is logged, never raised."""
    if not items:
        return
    db = get_firestore()
    if not db:
        return
    try:
        from firebase_admin import firestore

        db.collection(_BACKUP_COLLECTION).document(_BACKUP_DOC).set(
            {"items": items, "updatedAt": firestore.SERVER_TIMESTAMP}
        )
    except Exception as e:
        logger.warning(f"Could not save news backup to Firestore: {e}")


def _load_backup(limit: int) -> list[dict]:
    """Reads the last saved feed from Firestore. Returns [] if none or on error."""
    db = get_firestore()
    if not db:
        return []
    try:
        snap = db.collection(_BACKUP_COLLECTION).document(_BACKUP_DOC).get()
        if not snap.exists:
            return []
        items = (snap.to_dict() or {}).get("items") or []
        return items[:limit]
    except Exception as e:
        logger.warning(f"Could not load news backup from Firestore: {e}")
        return []


async def get_news_with_backup(limit: int = 24) -> tuple[list[dict], bool]:
    """Fetches live Hiru news, falling back to the Firestore backup when the live
    fetch comes back empty.

    Returns ``(items, from_backup)``: a successful live fetch is also saved to
    Firestore for next time; an empty live fetch serves the last saved feed.
    """
    items = await fetch_hiru_news(limit=limit)
    if items:
        _save_backup(items)
        return items, False

    logger.warning("Hiru live fetch returned no stories; serving Firestore backup.")
    return _load_backup(limit), True
