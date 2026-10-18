"""Tests for the Hiru News feed scraper and /news-feed endpoint."""

import pytest
from fastapi.testclient import TestClient

from airwave_agents.main import app
from airwave_agents.services import news_feed
from airwave_agents.services.news_feed import _parse_page, _clean

client = TestClient(app)


SAMPLE_HTML = """
<div class="row" style="margin-bottom:10px">
  <div class="column left"><div class="sc-image">
    <a href="https://hirunews.lk/goldfmnews/494630/rosamund-pike">
      <img src="x.jpg" alt="Rosamund Pike" /></a></div></div>
  <div class="column middle">
    <div class="all-section-tittle">
      <a href="https://hirunews.lk/goldfmnews/494630/rosamund-pike">Rosamund Pike highlights mine work</a>
    </div>
    <div class="middle-tittle-time">
      Saturday, 17 October 2026 - 9:49            </div>
    Oscar-nominated actress highlighted humanitarian mine clearance in Sri&nbsp;Lanka...  <a href="https://hirunews.lk/goldfmnews/494630/rosamund-pike" >Read More</a>
  </div>
</div>
<div class="row" style="margin-bottom:10px">
  <div class="column middle">
    <div class="all-section-tittle">
      <a href="https://hirunews.lk/goldfmnews/494604/heavy-rain">Heavy rainfall expected after 1 p.m.</a>
    </div>
    <div class="middle-tittle-time">Saturday, 17 October 2026 - 9:01</div>
    The Department of Meteorology says showers are likely today.  <a href="https://hirunews.lk/goldfmnews/494604/heavy-rain" >Read More</a>
  </div>
</div>
"""


def test_clean_strips_tags_and_entities():
    assert _clean("  <b>Hello</b>&nbsp;World  ") == "Hello World"


def test_parse_page_extracts_fields():
    items = _parse_page(SAMPLE_HTML, "Local")
    assert len(items) == 2

    first = items[0]
    assert first["id"] == "hiru_494630"
    assert first["title"] == "Rosamund Pike highlights mine work"
    assert first["category"] == "Local"
    assert first["published"] == "Saturday, 17 October 2026 - 9:49"
    assert first["link"].endswith("/494630/rosamund-pike")
    assert "humanitarian mine clearance" in first["description"]
    # Summary must not leak the trailing call-to-action.
    assert "Read More" not in first["description"]


def test_news_feed_endpoint(monkeypatch):
    async def fake_fetch(limit: int = 24):
        return [
            {
                "id": "hiru_1",
                "title": "Test story",
                "description": "Body",
                "category": "Local",
                "published": "today",
                "link": "https://hirunews.lk/goldfmnews/1/test",
                "order": 1,
            }
        ]

    monkeypatch.setattr(news_feed, "fetch_hiru_news", fake_fetch)
    # Router imported the symbol directly, so patch it there too.
    monkeypatch.setattr("airwave_agents.api.router.fetch_hiru_news", fake_fetch)

    resp = client.post("/api/v1/news-feed", json={"limit": 5})
    assert resp.status_code == 200
    data = resp.json()
    assert data["source"] == "Hiru News"
    assert data["count"] == 1
    assert data["items"][0]["id"] == "hiru_1"
    assert data["items"][0]["title"] == "Test story"


def test_news_feed_endpoint_empty_returns_502(monkeypatch):
    async def empty_fetch(limit: int = 24):
        return []

    monkeypatch.setattr("airwave_agents.api.router.fetch_hiru_news", empty_fetch)

    resp = client.post("/api/v1/news-feed", json={})
    assert resp.status_code == 502
    assert "detail" in resp.json()
