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

logger = logging.getLogger(__name__)

HIRU_BASE = "https://hirunews.lk/goldfmnews"

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
            try:
                resp = await client.get(f"{HIRU_BASE}/{page}")
                resp.raise_for_status()
                return _parse_page(resp.text, label)
            except Exception as e:  # network error, non-200, or markup change
                logger.warning(f"Hiru fetch failed for {page}: {e}")
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
