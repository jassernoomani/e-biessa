from io import BytesIO
from pathlib import Path
from threading import Lock

from PIL import Image, UnidentifiedImageError

from .config import Settings
from .models import VisionCandidate, VisionIdentificationResponse


class VisionUnavailable(RuntimeError):
    pass


class InvalidPartImage(ValueError):
    pass


PART_TAXONOMY = [
    ("brake caliper", "Brake Systems"), ("brake disc or rotor", "Brake Systems"),
    ("brake pad", "Brake Systems"), ("alternator", "Electrical Parts"),
    ("starter motor", "Electrical Parts"), ("headlight", "Lighting"),
    ("tail light", "Lighting"), ("side mirror", "Body Parts"),
    ("bumper", "Body Parts"), ("door", "Body Parts"),
    ("radiator", "Cooling System"), ("water pump", "Cooling System"),
    ("turbocharger", "Engine Parts"), ("engine cylinder head", "Engine Parts"),
    ("fuel injector", "Fuel System"), ("fuel pump", "Fuel System"),
    ("shock absorber", "Suspension"), ("control arm", "Suspension"),
    ("clutch kit", "Transmission"), ("gearbox", "Transmission"),
    ("exhaust muffler", "Exhaust System"), ("catalytic converter", "Exhaust System"),
]


class VisionPartIdentifier:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._model = None
        self._processor = None
        self._device = "cpu"
        self._lock = Lock()

    @property
    def ready(self) -> bool:
        return self._model is not None

    def identify(self, data: bytes) -> VisionIdentificationResponse:
        if not self.settings.vision_enabled:
            raise VisionUnavailable("Image identification is disabled. Set VISION_ENABLED=true after installing the vision dependencies.")
        image = self._validated_image(data)
        self._load()
        prompts = [f"a clear product photo of an automotive {part_type}" for part_type, _ in PART_TAXONOMY]
        inputs = self._processor(text=prompts, images=image, return_tensors="pt", padding=True)
        inputs = {key: value.to(self._device) for key, value in inputs.items()}
        with self._torch.inference_mode():
            probabilities = self._model(**inputs).logits_per_image.softmax(dim=1)[0]
        top = self._torch.topk(probabilities, k=min(5, len(PART_TAXONOMY)))
        candidates = [VisionCandidate(
            partType=PART_TAXONOMY[index][0], category=PART_TAXONOMY[index][1],
            confidence=round(float(score), 6),
        ) for score, index in zip(top.values.tolist(), top.indices.tolist())]
        return VisionIdentificationResponse(
            candidates=candidates, model=self.settings.vision_model,
            caution="Visual identification suggests part types only. Confirm the OEM number, dimensions, and vehicle fitment with the seller.",
        )

    def _validated_image(self, data: bytes) -> Image.Image:
        if not data:
            raise InvalidPartImage("Choose a non-empty JPEG, PNG, or WebP image.")
        if len(data) > self.settings.vision_max_image_bytes:
            raise InvalidPartImage("The image exceeds the configured size limit.")
        try:
            source = Image.open(BytesIO(data))
            source.verify()
            source = Image.open(BytesIO(data))
            if source.format not in {"JPEG", "PNG", "WEBP"}:
                raise InvalidPartImage("Only JPEG, PNG, and WebP images are supported.")
            if source.width * source.height > self.settings.vision_max_image_pixels:
                raise InvalidPartImage("The image dimensions are too large.")
            image = source.convert("RGB")
        except InvalidPartImage:
            raise
        except (UnidentifiedImageError, OSError, SyntaxError, ValueError):
            raise InvalidPartImage("The upload is not a valid JPEG, PNG, or WebP image.")
        return image

    def _load(self) -> None:
        if self._model is not None:
            return
        with self._lock:
            if self._model is not None:
                return
            try:
                import torch
                from transformers import CLIPModel, CLIPProcessor
            except ImportError as exception:
                raise VisionUnavailable("Install ai-service/requirements-vision.txt before enabling image identification.") from exception
            Path(self.settings.vision_cache_dir).mkdir(parents=True, exist_ok=True)
            self._torch = torch
            self._device = "cuda" if torch.cuda.is_available() else "cpu"
            self._processor = CLIPProcessor.from_pretrained(
                self.settings.vision_model, cache_dir=str(self.settings.vision_cache_dir))
            self._model = CLIPModel.from_pretrained(
                self.settings.vision_model, cache_dir=str(self.settings.vision_cache_dir)).to(self._device).eval()
