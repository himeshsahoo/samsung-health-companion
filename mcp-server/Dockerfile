FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY health_api.py .
ENV PYTHONUNBUFFERED=1
CMD ["sh", "-c", "uvicorn health_api:app --host 0.0.0.0 --port ${PORT:-8080}"]
