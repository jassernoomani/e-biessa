# Image-assisted identification and voice search

## Image-assisted identification

Signed-in users can open **AI fitment search** and upload one JPEG, PNG, or WebP image up to 5 MB. Angular sends the image to authenticated Spring Boot `POST /api/ai/identify`; Spring applies identity, rate, type, and size checks before forwarding the raw bytes to internal FastAPI `POST /v1/vision/identify` with the shared service token.

FastAPI uses CLIP with a 74-label vocabulary covering mechanical, body/exterior,
lighting and interior parts. Prompts include product photos and installed parts,
and preprocessing preserves the whole image instead of discarding its edges.
An optional local ResNet18 checkpoint supports its original 50 trained classes.
The remaining labels use zero-shot CLIP; they have not been fine-tuned on a new
dataset. Marketplace categories are consistent with the application's catalogue.

The response has `status: SUGGESTED` with one candidate, or `status: UNCERTAIN`
with no candidate and advice to crop the target, take another angle or type its
name. Blank/tiny uploads, non-part scenes, weak evidence and model disagreement
can cause abstention. Thresholds are conservative heuristics, not calibrated
probabilities; new classes without trained examples use stricter evidence rules.
The UI displays the part name and category without misleading probability badges.
Selecting a suggestion adds the part type to the existing compatibility-aware
search. Request images stay in memory and are not saved or used for training.

The feature deliberately does not infer make, model, year, OEM number, or fitment from pixels. The normal deterministic compatibility checks remain authoritative.

### Enable locally

1. Stop FastAPI and activate `ai-service/.venv`.
2. Install optional dependencies: `python -m pip install -r requirements-vision.txt`.
3. Add `VISION_ENABLED=true` to `ai-service/.env`.
4. Restart FastAPI. The first image request downloads `openai/clip-vit-base-patch32` into `ai-service/data/vision-models`; later requests reuse the cache.
5. Keep `AI_SERVICE_ENABLED=true` and use the same `AI_SERVICE_TOKEN` in Spring and FastAPI.

To use the verified local pilot checkpoint, also set:

```dotenv
VISION_CLASSIFIER_CHECKPOINT=./training/vision/runs/pilot-20261014/best.pt
```

The local `.env` is configured with this checkpoint. Install the updated optional
vision dependencies (including torchvision) in the environment that runs
FastAPI, then restart FastAPI and Spring Boot for the new code and response DTO.
Angular's development server reloads its template changes. This setting requires
the actual checkpoint file; Docker deployments need a matching read-only volume
mount and vision dependencies. The existing minimal Docker image is unchanged.
See [vision training and diagnostic results](../ai-service/training/vision/README.md).

The default remains disabled, so semantic search gains no new startup download. CPU inference works but is slower; a CUDA-enabled PyTorch build can use an available NVIDIA GPU.

## Voice search

The search bar provides French (Tunisia), Arabic (Tunisia), and English recognition choices. The browser Web Speech API turns speech into editable text; the user still decides when to submit the query. No audio is sent to or stored by E-biessa.

Voice recognition depends on browser support and may use the browser vendor's speech service. It works best on localhost or HTTPS in Chrome and Edge. Typed search remains the fallback.

Choose `FR`, `AR`, or `EN` before recording so the recognition locale matches the spoken language. Brave exposes parts of Chromium's speech API but may return a `network` error because its browser speech service is unavailable or blocked; use current Chrome or Edge for the academic demonstration. The interface distinguishes permission, microphone, no-speech, language, and network failures.

## Verification

FastAPI tests use a fake identifier and do not download CLIP. Spring integration tests verify authentication, upload validation, and the disabled-service response. Angular tests and Playwright cover candidate selection and speech-to-search behavior.
