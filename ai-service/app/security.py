from hmac import compare_digest

from typing import Annotated

from fastapi import Depends, Header, HTTPException, status

from .config import Settings, get_settings


def require_service_token(
    settings: Annotated[Settings, Depends(get_settings)],
    x_ai_service_token: str | None = Header(default=None),
) -> None:
    if not x_ai_service_token or not compare_digest(x_ai_service_token, settings.ai_service_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid AI service token")
