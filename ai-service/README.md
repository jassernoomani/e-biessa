# E-biessa AI service

This FastAPI service is the Phase 2 boundary for semantic listing retrieval. Spring Boot remains the system of record and authorization boundary. The service stores only a searchable projection of active listings.

## Local setup on Windows

1. Open PowerShell in `ai-service`.
2. Create a virtual environment: `py -3.12 -m venv .venv`
3. Activate it: `.\.venv\Scripts\Activate.ps1`
4. Install dependencies: `python -m pip install -r requirements.txt`
5. Copy `.env.example` to `.env` and replace `AI_SERVICE_TOKEN` with a long random value.
6. Start the service: `python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001`

Open `http://127.0.0.1:8001/health` to verify the service. API documentation is available at `http://127.0.0.1:8001/docs`.

The first indexing or search request downloads the multilingual embedding model and caches it under `data/models`. This can take several minutes. No OpenAI key and no Docker installation are required. Local Qdrant data is persisted under `data/qdrant`.

For a shared or production environment, set `QDRANT_URL` and `QDRANT_API_KEY` to use Qdrant Server or Qdrant Cloud instead of the embedded local store.

## Trained query extraction

The optional local Ollama model extracts make, model, year, fuel, position, category, and part type before vector retrieval. Configure it in `.env`:

```dotenv
OLLAMA_ENABLED=true
OLLAMA_URL=http://127.0.0.1:11434
OLLAMA_MODEL=e-biessa-query-v2
OLLAMA_TIMEOUT_SECONDS=90
OLLAMA_KEEP_ALIVE=30m
```

Register the fine-tuned adapter by following `training/README.md`, make sure Ollama is running, and then restart FastAPI. The first request after the model is unloaded can take about a minute on an 8 GB RTX 4060; subsequent requests are much faster while the model remains loaded. If Ollama is stopped, times out, or returns invalid JSON, search continues with the deterministic query parser instead of failing.

The `/health` response exposes `queryModelEnabled` and `queryModel`, which confirms configuration but does not force-load the model.

## Retrieval evaluation

After starting Ollama, FastAPI, and Spring Boot and reindexing active listings, run the multilingual end-to-end benchmark from `ai-service`:

```powershell
.\.venv\Scripts\python.exe training\evaluate_retrieval.py
```

The benchmark measures hit rate at 12, mean reciprocal rank, validated-constraint accuracy, and latency. Ollama is used only for structured constraint extraction; answer text and recommended IDs are generated deterministically from active database-backed listings. The benchmark exits unsuccessfully when any expected listing is missing or constraint accuracy falls below 95%. Review and expand `training/data/retrieval_benchmark.json` whenever the seed catalog or supported local terminology changes.

## Security

All indexing and search endpoints require `X-AI-Service-Token`. Use the same value for `AI_SERVICE_TOKEN` in this service and the Spring Boot application. Do not expose this service directly to the browser or commit the real token.
