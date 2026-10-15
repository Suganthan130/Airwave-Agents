"""Tests for health and routing."""

import pytest
from fastapi.testclient import TestClient
from airwave_agents.main import app

client = TestClient(app)


def test_root():
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "online"
    assert data["runtime"] == "Google Cloud Run"


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_unauthenticated_api_returns_401():
    response = client.post("/api/v1/rj-line", json={"type": "joke"})
    assert response.status_code == 401
    assert "detail" in response.json()
