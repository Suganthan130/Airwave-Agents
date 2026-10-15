"""Main FastAPI application entry point for Airwave Agents Cloud Run service."""

import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from airwave_agents.api.router import router
from airwave_agents.core.firebase import get_firestore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("airwave_agents")

app = FastAPI(
    title="Airwave Agents API",
    description="Gemini-powered AI radio co-host brain for live broadcasting",
    version="1.0.0",
)

# CORS configuration for web clients and transmitter
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API v1 router
app.include_router(router)


@app.on_event("startup")
async def startup_event():
    logger.info("Starting up Airwave Agents API on Google Cloud Run...")
    get_firestore()


@app.get("/", tags=["Health"])
async def root():
    return {
        "service": "Airwave Agents API",
        "runtime": "Google Cloud Run",
        "model": "gemini-2.5-flash",
        "status": "online",
        "version": "1.0.0",
    }


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok"}
