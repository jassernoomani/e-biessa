from io import BytesIO
from pathlib import Path
from threading import Lock

from PIL import Image, ImageOps, UnidentifiedImageError

from .config import Settings
from .models import VisionCandidate, VisionIdentificationResponse
from .vision_taxonomy import PART_TAXONOMY, NON_PART_PROMPTS, DETAIL_PROMPTS, CLASSIFIER_LABELS
from .vision_policy import choose_part


class VisionUnavailable(RuntimeError):
    pass


class InvalidPartImage(ValueError):
    pass


class VisionPartIdentifier:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._model = None
        self._processor = None
        self._device = "cpu"
        self._lock = Lock()
        self._classifier = None
        self._classifier_classes = []
        self._text_features = None

    @property
    def ready(self) -> bool:
        return self._model is not None

    def identify(self, data: bytes) -> VisionIdentificationResponse:
        if not self.settings.vision_enabled:
            raise VisionUnavailable("Image identification is disabled. Set VISION_ENABLED=true after installing the vision dependencies.")
        image = self._validated_image(data)
        if min(image.size) < 32 or max(high - low for low, high in image.getextrema()) < 4:
            return VisionIdentificationResponse(candidates=[], model=self.settings.vision_model,
                status="UNCERTAIN", caution="The photo is blank, too small, or lacks visible detail. Upload a clear close-up of the part.")
        self._load()
        # Preserve the whole part instead of letting CLIP's square centre crop
        # discard door handles, exhaust ends, or the top of a portrait photo.
        clip_image = ImageOps.pad(image, (224, 224), method=Image.Resampling.LANCZOS,
                                 color=(127, 127, 127))
        inputs = self._processor(images=clip_image, return_tensors="pt")
        inputs = {key: value.to(self._device) for key, value in inputs.items()}
        with self._torch.inference_mode():
            features = self._model.get_image_features(**inputs)
            if not isinstance(features, self._torch.Tensor):
                features = features.pooler_output
            features = features / features.norm(dim=-1, keepdim=True)
            similarities = (features @ self._text_features.T)[0]
            probabilities = (similarities * self._model.logit_scale.exp()).softmax(dim=0)
            classifier = None
            if self._classifier is not None:
                pixels = self._classifier_transform(image).unsqueeze(0).to(self._device)
                scores = self._classifier(pixels).softmax(1)[0]
                top = scores.topk(min(5, len(self._classifier_classes)))
                classifier = [(self._classifier_classes[index], score) for score, index in zip(top.values.tolist(), top.indices.tolist())]
        choice, reason = choose_part(probabilities.tolist(), similarities.tolist(), classifier,
            self.settings.vision_minimum_score, self.settings.vision_minimum_margin,
            self.settings.vision_minimum_similarity, self.settings.vision_minimum_similarity_margin,
            self.settings.vision_minimum_untrained_score)
        candidates = []
        if choice is not None:
            index, score = choice
            candidates = [VisionCandidate(partType=PART_TAXONOMY[index][0],
                category=PART_TAXONOMY[index][1], confidence=round(score, 6))]
        return VisionIdentificationResponse(
            candidates=candidates, model=self.settings.vision_model + (" + resnet18" if self._classifier is not None else ""),
            status="SUGGESTED" if candidates else "UNCERTAIN",
            caution=(reason + " " if reason else "") + "Photo identification suggests a part type only. Confirm the OEM number, dimensions, and vehicle fitment with the seller.",
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
            processor = CLIPProcessor.from_pretrained(
                self.settings.vision_model, cache_dir=str(self.settings.vision_cache_dir))
            model = CLIPModel.from_pretrained(
                self.settings.vision_model, cache_dir=str(self.settings.vision_cache_dir)).to(self._device).eval()
            prompts = []
            groups = []
            for part_type, _ in PART_TAXONOMY:
                variants = [f"a clear product photo of an automotive {part_type}",
                    f"a close-up photo of a {part_type} fitted to a car",
                    DETAIL_PROMPTS.get(part_type, f"a photo of an automotive {part_type}")]
                groups.append(len(variants))
                prompts.extend(variants)
            prompts.extend(NON_PART_PROMPTS)
            groups.extend([1] * len(NON_PART_PROMPTS))
            tokens = processor(text=prompts, return_tensors="pt", padding=True)
            tokens = {key: value.to(self._device) for key, value in tokens.items()}
            with torch.inference_mode():
                features = model.get_text_features(**tokens)
                if not isinstance(features, torch.Tensor):
                    features = features.pooler_output
                features = features / features.norm(dim=-1, keepdim=True)
                text_features = torch.stack([chunk.mean(0) for chunk in features.split(groups)])
                text_features = text_features / text_features.norm(dim=-1, keepdim=True)
            checkpoint_path = self.settings.vision_classifier_checkpoint
            if checkpoint_path is not None:
                if not checkpoint_path.is_file():
                    raise VisionUnavailable("The configured vision classifier checkpoint is missing.")
                try:
                    from torchvision import models
                    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
                    classes = checkpoint["classes"]
                    if len(set(classes)) != len(classes) or not all(label in CLASSIFIER_LABELS for label in classes):
                        raise ValueError("Unsupported classifier labels")
                    classifier = models.resnet18(weights=None)
                    classifier.fc = torch.nn.Linear(classifier.fc.in_features, len(classes))
                    classifier.load_state_dict(checkpoint["state_dict"])
                    self._classifier = classifier.to(self._device).eval()
                    self._classifier_classes = classes
                    self._classifier_transform = models.ResNet18_Weights.DEFAULT.transforms()
                except (ImportError, OSError, ValueError, KeyError, RuntimeError) as exception:
                    raise VisionUnavailable("The vision classifier could not be loaded. Check its checkpoint and torchvision installation.") from exception
            self._processor = processor
            self._text_features = text_features
            self._model = model
