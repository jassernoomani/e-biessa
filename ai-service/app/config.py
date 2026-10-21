from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    ai_service_token: str = Field(min_length=24)
    ai_host: str = "127.0.0.1"
    ai_port: int = 8001
    qdrant_path: Path = Path("./data/qdrant")
    qdrant_url: str | None = None
    qdrant_api_key: str | None = None
    qdrant_collection: str = "e_biessa_parts"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_cache_dir: Path = Path("./data/models")
    ollama_enabled: bool = False
    ollama_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "e-biessa-query-v2"
    ollama_timeout_seconds: float = Field(default=90.0, gt=0, le=120)
    ollama_keep_alive: str = "30m"
    vision_enabled: bool = False
    vision_model: str = "openai/clip-vit-base-patch32"
    vision_cache_dir: Path = Path("./data/vision-models")
    vision_classifier_checkpoint: Path | None = None
    vision_minimum_score: float = Field(default=0.35, ge=0, le=1)
    vision_minimum_margin: float = Field(default=0.12, ge=0, le=1)
    vision_minimum_similarity: float = Field(default=0.20, ge=0, le=1)
    vision_minimum_similarity_margin: float = Field(default=0.01, ge=0, le=1)
    vision_minimum_untrained_score: float = Field(default=0.85, ge=0, le=1)
    vision_max_image_bytes: int = Field(default=5_242_880, ge=1024, le=10_485_760)
    vision_max_image_pixels: int = Field(default=25_000_000, ge=1_000_000, le=100_000_000)


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
