"""Authenticated cloud API for Samsung Health Companion snapshots.

Run locally:
  export SYNC_AUTH_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
  uvicorn health_api:app --host 0.0.0.0 --port 8080

For production, set DATABASE_URL to a managed PostgreSQL connection string.
"""
from __future__ import annotations

import hmac
import json
import os
from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException, Query, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import DateTime, Integer, String, Text, create_engine, desc, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./health_companion.db")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    pass


class HealthSnapshot(Base):
    __tablename__ = "health_snapshots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    sync_id: Mapped[str] = mapped_column(String(36), unique=True, index=True)
    device_timestamp: Mapped[str] = mapped_column(String(40), index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    payload_json: Mapped[str] = mapped_column(Text)


Base.metadata.create_all(engine)

app = FastAPI(
    title="Samsung Health Companion API",
    version="1.0.0",
    description=(
        "Private health snapshot API. All health-data routes require a bearer token. "
        "Use this API as the shared data source for ChatGPT GPT Actions and Gemini function calling."
    ),
)
bearer = HTTPBearer(auto_error=False)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def require_sync_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> None:
    expected = os.getenv("SYNC_AUTH_TOKEN")
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Server authentication is not configured.",
        )
    supplied = credentials.credentials if credentials else ""
    if not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid bearer token.")


@app.get("/health/ready", tags=["operations"])
def readiness():
    return {"status": "ok", "database": "configured"}


@app.post("/v1/sync", tags=["health"], dependencies=[Depends(require_sync_token)])
async def receive_snapshot(request: Request, db: Session = Depends(get_db)):
    raw = await request.body()
    if len(raw) > 5 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Snapshot exceeds the 5 MiB limit.")
    try:
        payload: dict[str, Any] = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError):
        raise HTTPException(status_code=400, detail="Body must be valid JSON.")
    if not isinstance(payload, dict):
        raise HTTPException(status_code=422, detail="Snapshot must be a JSON object.")

    sync_id = payload.get("sync_id")
    timestamp = payload.get("timestamp")
    if not isinstance(sync_id, str) or not isinstance(timestamp, str):
        raise HTTPException(status_code=422, detail="sync_id and timestamp are required.")
    try:
        canonical_sync_id = str(UUID(sync_id))
        datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        raise HTTPException(status_code=422, detail="sync_id or timestamp has an invalid format.")

    existing = db.scalar(select(HealthSnapshot).where(HealthSnapshot.sync_id == canonical_sync_id))
    if existing:
        existing.device_timestamp = timestamp
        existing.received_at = datetime.now(timezone.utc)
        existing.payload_json = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
    else:
        db.add(HealthSnapshot(
            sync_id=canonical_sync_id,
            device_timestamp=timestamp,
            received_at=datetime.now(timezone.utc),
            payload_json=json.dumps(payload, separators=(",", ":"), ensure_ascii=False),
        ))
    db.commit()
    return {"status": "accepted", "sync_id": canonical_sync_id, "schema_version": payload.get("schema_version", 1)}


@app.get("/v1/health/latest", tags=["health"], dependencies=[Depends(require_sync_token)])
def latest_snapshot(db: Session = Depends(get_db)):
    record = db.scalar(select(HealthSnapshot).order_by(desc(HealthSnapshot.received_at)).limit(1))
    if not record:
        raise HTTPException(status_code=404, detail="No health snapshot has been synced yet.")
    return json.loads(record.payload_json)


@app.get("/v1/health/history", tags=["health"], dependencies=[Depends(require_sync_token)])
def health_history(
    days: int = Query(default=30, ge=1, le=365),
    limit: int = Query(default=100, ge=1, le=500),
    db: Session = Depends(get_db),
):
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    records = db.scalars(
        select(HealthSnapshot)
        .where(HealthSnapshot.received_at >= cutoff)
        .order_by(desc(HealthSnapshot.received_at))
        .limit(limit)
    ).all()
    return {
        "days": days,
        "count": len(records),
        "snapshots": [json.loads(record.payload_json) for record in records],
    }
