# Cloud API deployment

The Android client posts versioned health snapshots to `POST /v1/sync`.
The API persists snapshots in PostgreSQL and exposes authenticated read endpoints
for ChatGPT GPT Actions and Gemini function calling.

## Required environment variables

- `SYNC_AUTH_TOKEN`: generate a unique random secret (at least 32 bytes). Use the same value in the Android app's Cloud Sync token field. Never commit it.
- `DATABASE_URL`: managed PostgreSQL connection string. The API also supports local SQLite for development.

## Run locally

```bash
cd mcp-server
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export SYNC_AUTH_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
uvicorn health_api:app --host 0.0.0.0 --port 8080
```

Set the Android app's cloud endpoint to `https://YOUR-HOST/v1/sync` and its bearer token
to the value of `SYNC_AUTH_TOKEN`.

## Deploy

1. Create a managed PostgreSQL database with your cloud host.
2. Deploy the `mcp-server` directory as a Python web service.
3. Install dependencies with `pip install -r requirements.txt`.
4. Start with `uvicorn health_api:app --host 0.0.0.0 --port $PORT`.
5. Set `DATABASE_URL` and `SYNC_AUTH_TOKEN` as private environment variables.
6. Confirm `/health/ready` returns `{"status":"ok","database":"configured"}`.

Do not enable public health-data routes. The readiness route contains no personal data;
all `/v1/*` endpoints require the bearer token.

## AI connections

- ChatGPT: create a Custom GPT, add an Action using the API's `/openapi.json`, and configure API-key authentication using the same bearer token.
- Gemini: call `GET /v1/health/latest` and `GET /v1/health/history` from a server-side Gemini function/tool. Do not embed the bearer token in a public client app or prompt.
