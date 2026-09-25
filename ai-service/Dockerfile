FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1
WORKDIR /app
RUN addgroup --system ebiessa && adduser --system --ingroup ebiessa ebiessa
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt
COPY app app
RUN mkdir -p /app/data/qdrant /app/data/models && chown -R ebiessa:ebiessa /app
USER ebiessa
EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health', timeout=3)" || exit 1
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
