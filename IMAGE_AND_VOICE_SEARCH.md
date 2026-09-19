# Image-assisted identification and voice search

## Image-assisted identification

Signed-in users can open **AI fitment search** and upload one JPEG, PNG, or WebP image up to 5 MB. Angular sends the image to authenticated Spring Boot `POST /api/ai/identify`; Spring applies identity, rate, type, and size checks before forwarding the raw bytes to internal FastAPI `POST /v1/vision/identify` with the shared service token.

FastAPI uses CLIP zero-shot classification over a bounded automotive-part taxonomy. It returns up to five candidate part types with relative confidence. The image is held only in request memory and is not saved, indexed, or used for training. Selecting a candidate adds the part type to the existing compatibility-aware search.

The feature deliberately does not infer make, model, year, OEM number, or fitment from pixels. The normal deterministic compatibility checks remain authoritative.

### Enable locally

1. Stop FastAPI and activate `ai-service/.venv`.
2. Install optional dependencies: `python -m pip install -r requirements-vision.txt`.
3. Add `VISION_ENABLED=true` to `ai-service/.env`.
4. Restart FastAPI. The first image request downloads `openai/clip-vit-base-patch32` into `ai-service/data/vision-models`; later requests reuse the cache.
5. Keep `AI_SERVICE_ENABLED=true` and use the same `AI_SERVICE_TOKEN` in Spring and FastAPI.

The default remains disabled, so semantic search gains no new startup download. CPU inference works but is slower; a CUDA-enabled PyTorch build can use an available NVIDIA GPU.

## Voice search

The search bar provides French (Tunisia), Arabic (Tunisia), and English recognition choices. The browser Web Speech API turns speech into editable text; the user still decides when to submit the query. No audio is sent to or stored by E-biessa.

Voice recognition depends on browser support and may use the browser vendor's speech service. It works best on localhost or HTTPS in Chrome and Edge. Typed search remains the fallback.

## Verification

FastAPI tests use a fake identifier and do not download CLIP. Spring integration tests verify authentication, upload validation, and the disabled-service response. Angular tests and Playwright cover candidate selection and speech-to-search behavior.
