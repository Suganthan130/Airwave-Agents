"""Firebase ID Token authentication dependency for FastAPI."""

import logging
from fastapi import HTTPException, Security, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from firebase_admin import auth
from airwave_agents.core.firebase import get_firestore

logger = logging.getLogger(__name__)

security = HTTPBearer(auto_error=False)


async def get_current_user(credentials: HTTPAuthorizationCredentials | None = Security(security)) -> dict:
    """Verifies Firebase ID token from 'Authorization: Bearer <token>' header."""
    # Ensure Firebase Admin is initialized
    get_firestore()

    if not credentials or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required. Please provide a valid Bearer token.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = credentials.credentials.strip()
    try:
        decoded_token = auth.verify_id_token(token)
        return decoded_token
    except auth.ExpiredIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has expired. Please refresh your session.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except auth.RevokedIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has been revoked.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as e:
        logger.warning(f"Failed to verify Firebase ID token: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid authentication token: {str(e)}",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_optional_user(credentials: HTTPAuthorizationCredentials | None = Security(security)) -> dict | None:
    """Verifies Firebase ID token if present; returns None if missing or expired without throwing 401."""
    get_firestore()
    if not credentials or not credentials.credentials:
        return None
    token = credentials.credentials.strip()
    try:
        return auth.verify_id_token(token)
    except Exception as e:
        logger.info(f"Optional user verification omitted: {e}")
        return None

