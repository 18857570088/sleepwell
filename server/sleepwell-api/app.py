import hashlib
import json
import os
from datetime import date, datetime, time
from pathlib import Path
from typing import Any

import pymysql
from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware


APP_VERSION = "0.1.0"


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


DB_CONFIG = {
    "host": env("SLEEPWELL_DB_HOST", "127.0.0.1"),
    "port": int(env("SLEEPWELL_DB_PORT", "3306")),
    "user": env("SLEEPWELL_DB_USER", "wm"),
    "password": env("SLEEPWELL_DB_PASSWORD", ""),
    "database": env("SLEEPWELL_DB_NAME", "sleepwell"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
    "autocommit": True,
}

UPLOAD_DIR = Path(env("SLEEPWELL_UPLOAD_DIR", "/var/lib/sleepwell-api/uploads"))
APP_USER_ID = env("SLEEPWELL_DEFAULT_USER", "demo")

app = FastAPI(title="Sleepwell API", version=APP_VERSION)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


def db() -> pymysql.Connection:
    return pymysql.connect(**DB_CONFIG)


def json_value(value: Any) -> Any:
    if isinstance(value, (datetime, date, time)):
        return value.isoformat()
    return value


def sleep_record(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "date": json_value(row["record_date"]),
        "bedTime": row["bed_time"],
        "wakeTime": row["wake_time"],
        "totalSleepMinutes": row["total_sleep_minutes"],
        "sleepLatencyMinutes": row["sleep_latency_minutes"],
        "sleepEfficiency": row["sleep_efficiency"],
        "awakenings": row["awakenings"],
        "awakeAfterSleepMinutes": row["awake_after_sleep_minutes"],
        "deepSleepMinutes": row["deep_sleep_minutes"],
        "lightSleepMinutes": row["light_sleep_minutes"],
        "remSleepMinutes": row["rem_sleep_minutes"],
        "sleepDebtMinutes": row["sleep_debt_minutes"],
        "score": row["score"],
        "midpoint": row["midpoint"],
        "recommendedMidpoint": row["recommended_midpoint"],
        "stability": row["stability"],
        "sleepTiming": row["sleep_timing"],
    }


def content_item(row: dict[str, Any]) -> dict[str, Any]:
    tags = row.get("tags_json") or "[]"
    if isinstance(tags, str):
        try:
            tags = json.loads(tags)
        except json.JSONDecodeError:
            tags = []
    return {
        "id": row["content_id"],
        "type": row["type"],
        "title": row["title"],
        "subtitle": row["subtitle"],
        "duration": row["duration"],
        "cover": row["cover_path"],
        "url": row["media_path"],
        "tags": tags,
        "description": row["description"],
    }


@app.get("/health")
def health() -> dict[str, Any]:
    try:
        with db() as conn, conn.cursor() as cur:
            cur.execute("SELECT 1 AS ok")
            cur.fetchone()
        db_status = "ok"
    except Exception as exc:
        db_status = f"error: {exc.__class__.__name__}"
    return {
        "status": "ok" if db_status == "ok" else "degraded",
        "service": "sleepwell-api",
        "version": APP_VERSION,
        "database": db_status,
        "time": datetime.utcnow().isoformat() + "Z",
    }


@app.get("/v1/sleep-records")
def list_sleep_records(
    days: int = Query(7, ge=1, le=90),
    user_id: str = Query(APP_USER_ID, min_length=1),
) -> dict[str, Any]:
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT sr.*
            FROM sleep_records sr
            JOIN app_users u ON u.id = sr.user_id
            WHERE u.external_user_id = %s
            ORDER BY sr.record_date DESC
            LIMIT %s
            """,
            (user_id, days),
        )
        rows = list(reversed(cur.fetchall()))
    return {"data": [sleep_record(row) for row in rows]}


@app.get("/v1/health-contents")
def list_health_contents(content_type: str | None = None) -> dict[str, Any]:
    sql = "SELECT * FROM health_contents WHERE status = 'active'"
    params: list[Any] = []
    if content_type:
        sql += " AND type = %s"
        params.append(content_type)
    sql += " ORDER BY sort_order ASC, id ASC"
    with db() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    return {"data": [content_item(row) for row in rows]}


@app.get("/v1/device/status")
def device_status(user_id: str = Query(APP_USER_ID, min_length=1)) -> dict[str, Any]:
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT dbn.device_sn, dbn.device_name, dbn.status, dbn.firmware_version, dbn.last_seen_at
            FROM device_bindings dbn
            JOIN app_users u ON u.id = dbn.user_id
            WHERE u.external_user_id = %s
            ORDER BY dbn.last_seen_at DESC, dbn.id DESC
            LIMIT 1
            """,
            (user_id,),
        )
        row = cur.fetchone()
    if not row:
        return {"data": {"status": "unbound", "deviceName": None, "lastSeenAt": None}}
    return {
        "data": {
            "deviceSn": row["device_sn"],
            "deviceName": row["device_name"],
            "status": row["status"],
            "firmwareVersion": row["firmware_version"],
            "lastSeenAt": json_value(row["last_seen_at"]),
        }
    }


@app.post("/v1/uploads")
async def upload_file(category: str = "education", file: UploadFile = File(...)) -> dict[str, Any]:
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty file")
    digest = hashlib.sha256(raw).hexdigest()
    suffix = Path(file.filename or "upload.bin").suffix
    stored_name = f"{datetime.utcnow().strftime('%Y%m%d%H%M%S')}_{digest[:16]}{suffix}"
    target = UPLOAD_DIR / stored_name
    target.write_bytes(raw)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO upload_files
                (category, original_name, stored_name, storage_path, mime_type, size_bytes, sha256)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """,
            (category, file.filename, stored_name, str(target), file.content_type, len(raw), digest),
        )
        file_id = cur.lastrowid
    return {"data": {"id": file_id, "storedName": stored_name, "sizeBytes": len(raw), "sha256": digest}}
