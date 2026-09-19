from io import BytesIO

import pytest
from PIL import Image

from app.config import Settings
from app.vision_identifier import InvalidPartImage, VisionPartIdentifier, VisionUnavailable


TOKEN = "test-token-that-is-at-least-24-characters"
buffer = BytesIO()
Image.new("RGB", (1, 1), (230, 80, 40)).save(buffer, format="PNG")
ONE_PIXEL_PNG = buffer.getvalue()


def test_validator_accepts_a_small_real_png_without_loading_clip():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=False))
    image = identifier._validated_image(ONE_PIXEL_PNG)
    assert image.size == (1, 1)
    assert image.mode == "RGB"


def test_disabled_identifier_fails_before_loading_any_model():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=False))
    with pytest.raises(VisionUnavailable, match="disabled"):
        identifier.identify(ONE_PIXEL_PNG)


def test_validator_rejects_non_images_and_oversized_dimensions():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=True))
    with pytest.raises(InvalidPartImage, match="valid JPEG"):
        identifier._validated_image(b"not an image")

    strict = VisionPartIdentifier(Settings(
        ai_service_token=TOKEN, vision_enabled=True, vision_max_image_pixels=1_000_000
    ))
    strict.settings.vision_max_image_bytes = 1024
    with pytest.raises(InvalidPartImage, match="size limit"):
        strict._validated_image(b"x" * 1025)
