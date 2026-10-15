"""Firebase Admin SDK setup for Cloud Run."""

import logging
import firebase_admin
from firebase_admin import firestore
from airwave_agents.core.config import PROJECT_ID

logger = logging.getLogger(__name__)

_app = None
_db = None


def get_firestore():
    """Returns singleton Firestore client using Application Default Credentials."""
    global _app, _db
    if _db is not None:
        return _db

    try:
        if not firebase_admin._apps:
            _app = firebase_admin.initialize_app(options={"projectId": PROJECT_ID})
        else:
            _app = firebase_admin.get_app()
        _db = firestore.client()
        logger.info(f"Connected to Firestore in project {PROJECT_ID}")
    except Exception as e:
        logger.warning(f"Firestore initialization warning: {e}")
        _db = None
    return _db
