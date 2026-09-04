import json
import logging
from typing import Any

import httpx

from .models import ExtractedConstraints, GroundedAnswer, SearchHit


LOGGER = logging.getLogger(__name__)

EXTRACTION_INSTRUCTION = """Extract automotive search constraints from the buyer request.
Return only JSON with exactly these keys: brand, model, year, fuelType, position, category, partType.
Use null when a value is not stated or cannot be inferred safely. Copy a stated year as a complete four-digit integer.
Normalize fuelType to Diesel or Petrol. Use canonical marketplace category and part-type names."""

CONSTRAINT_SCHEMA = {
    "type": "object",
    "properties": {
        "brand": {"type": ["string", "null"]},
        "model": {"type": ["string", "null"]},
        "year": {"type": ["integer", "string", "null"]},
        "fuelType": {"type": ["string", "null"]},
        "position": {"type": ["string", "null"]},
        "category": {"type": ["string", "null"]},
        "partType": {"type": ["string", "null"]},
    },
    "required": ["brand", "model", "year", "fuelType", "position", "category", "partType"],
    "additionalProperties": False,
}

FITMENT_CAUTION = "Confirm the OEM or interchange number and exact fitment with the seller before buying."


class OllamaConstraintExtractor:
    def __init__(
        self,
        base_url: str,
        model: str,
        timeout_seconds: float = 90.0,
        keep_alive: str = "30m",
    ):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.keep_alive = keep_alive

    def extract(self, query: str) -> dict[str, Any] | None:
        try:
            response = httpx.post(
                f"{self.base_url}/api/chat",
                json={
                    "model": self.model,
                    "stream": False,
                    "keep_alive": self.keep_alive,
                    "format": CONSTRAINT_SCHEMA,
                    "messages": [
                        {"role": "system", "content": EXTRACTION_INSTRUCTION},
                        {"role": "user", "content": query},
                    ],
                    "options": {"temperature": 0, "num_predict": 160},
                },
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            content = response.json().get("message", {}).get("content", "")
            parsed = json.loads(content)
            return parsed if isinstance(parsed, dict) else None
        except (httpx.HTTPError, json.JSONDecodeError, TypeError, ValueError) as error:
            LOGGER.warning("Ollama constraint extraction failed; using deterministic fallback: %s", error)
            return None


def grounded_answer_fallback(
    hits: list[SearchHit],
    constraints: ExtractedConstraints,
) -> GroundedAnswer:
    if not hits:
        stated = [str(value) for value in constraints.model_dump().values() if value is not None]
        suffix = f" for {' / '.join(stated)}" if stated else ""
        return GroundedAnswer(
            summary=f"No active listing met all validated compatibility requirements{suffix}.",
            cautions=["Try removing one optional constraint or ask a seller to confirm an alternative part."],
        )
    best = hits[0]
    return GroundedAnswer(
        summary=(
            f"Found {len(hits)} compatibility-checked {'listing' if len(hits) == 1 else 'listings'}. "
            f"The strongest current match is Listing #{best.listingId}: {best.title}."
        ),
        recommendedListingIds=[hit.listingId for hit in hits[:3]],
        cautions=[FITMENT_CAUTION],
    )
