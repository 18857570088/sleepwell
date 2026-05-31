import hashlib
import json
import os
import re
from datetime import date, datetime, time
from pathlib import Path
from typing import Any

import pymysql
from fastapi import FastAPI, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel


APP_VERSION = "0.2.4"


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
PUBLIC_BASE_PATH = env("SLEEPWELL_PUBLIC_BASE_PATH", "/sleepwell-api").rstrip("/")
ADMIN_TOKEN = env("SLEEPWELL_ADMIN_TOKEN", "")
UPLOAD_TOOL_PATH = Path(__file__).with_name("upload_tool.html")

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Sleepwell API", version=APP_VERSION)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)
app.mount("/media", StaticFiles(directory=str(UPLOAD_DIR)), name="media")


class SurveyRecommendationRequest(BaseModel):
    answers: dict[str, Any] = {}
    categories: list[str] = []


class ContentProgressRequest(BaseModel):
    userId: str = APP_USER_ID
    contentIds: list[str] = []
    progressPercent: int = 100
    lastPositionSeconds: int = 0
    completed: bool = True


SURVEY_TRIGGER_OPTIONS = [
    {
        "key": "drinkingHabit",
        "label": "饮酒习惯",
        "multiple": False,
        "answers": [
            {"key": "yes", "label": "是"},
            {"key": "no", "label": "否"},
        ],
    },
    {
        "key": "drinkingTimes",
        "label": "习惯饮酒时间",
        "multiple": True,
        "answers": [
            {"key": "morning", "label": "早"},
            {"key": "noon", "label": "中"},
            {"key": "evening", "label": "晚"},
            {"key": "before_sleep", "label": "睡前"},
        ],
    },
    {
        "key": "smokingHabit",
        "label": "吸烟习惯",
        "multiple": False,
        "answers": [
            {"key": "yes", "label": "是"},
            {"key": "no", "label": "否"},
        ],
    },
    {
        "key": "smokingTimes",
        "label": "习惯吸烟时间",
        "multiple": True,
        "answers": [
            {"key": "daytime", "label": "白天"},
            {"key": "evening", "label": "晚间抽烟"},
            {"key": "before_sleep", "label": "睡前"},
        ],
    },
    {
        "key": "teaHabit",
        "label": "喝茶习惯",
        "multiple": False,
        "answers": [
            {"key": "yes", "label": "是"},
            {"key": "no", "label": "否"},
        ],
    },
    {
        "key": "teaTimes",
        "label": "习惯喝茶时间",
        "multiple": True,
        "answers": [
            {"key": "morning", "label": "上午"},
            {"key": "afternoon", "label": "下午"},
            {"key": "evening", "label": "晚上"},
            {"key": "before_sleep", "label": "睡前"},
        ],
    },
    {
        "key": "caffeineHabit",
        "label": "含咖啡因类饮料习惯（可乐/咖啡）",
        "multiple": False,
        "answers": [
            {"key": "yes", "label": "是"},
            {"key": "no", "label": "否"},
        ],
    },
    {
        "key": "caffeineTimes",
        "label": "习惯饮用含咖啡因类饮料时间",
        "multiple": True,
        "answers": [
            {"key": "morning", "label": "上午"},
            {"key": "afternoon", "label": "下午"},
            {"key": "evening", "label": "晚上"},
            {"key": "before_sleep", "label": "睡前"},
        ],
    },
    {
        "key": "exerciseHabit",
        "label": "运动习惯",
        "multiple": False,
        "answers": [
            {"key": "yes", "label": "有"},
            {"key": "no", "label": "无"},
        ],
    },
    {
        "key": "exerciseType",
        "label": "习惯运动类型",
        "multiple": False,
        "answers": [
            {"key": "aerobic", "label": "有氧"},
            {"key": "anaerobic", "label": "无氧"},
        ],
    },
    {
        "key": "exerciseProjects",
        "label": "习惯运动项目",
        "multiple": True,
        "answers": [
            {"key": "walk", "label": "散步"},
            {"key": "brisk_walk", "label": "快走"},
            {"key": "jogging", "label": "慢跑"},
            {"key": "race_walking", "label": "竞走"},
            {"key": "swimming", "label": "游泳"},
            {"key": "cycling", "label": "骑自行车"},
            {"key": "tai_chi", "label": "打太极拳"},
            {"key": "dance", "label": "跳舞"},
            {"key": "aerobics", "label": "做韵律操"},
            {"key": "rope_skipping", "label": "跳绳"},
            {"key": "basketball", "label": "打篮球"},
            {"key": "football", "label": "踢足球"},
        ],
    },
    {
        "key": "bedtimeBehaviors",
        "label": "睡前行为",
        "multiple": True,
        "answers": [
            {"key": "exercise_before_bed", "label": "睡前3小时内运动"},
            {"key": "electronics_before_bed", "label": "睡前看电子产品"},
            {"key": "clock_watching", "label": "夜间睡不着时看时间"},
        ],
    },
    {
        "key": "unfamiliarSleepEnvironment",
        "label": "陌生睡眠环境",
        "multiple": False,
        "answers": [
            {"key": "better", "label": "睡得比平常更好"},
            {"key": "same", "label": "睡得跟平常一样"},
            {"key": "worse", "label": "睡得比平常要差"},
        ],
    },
    {
        "key": "sleepImpact",
        "label": "睡眠不好对生活的影响",
        "multiple": True,
        "answers": [
            {"key": "worry_tension", "label": "担心/紧张"},
            {"key": "mistakes", "label": "容易犯错"},
            {"key": "poor_attention", "label": "注意力不集中"},
            {"key": "memory_decline", "label": "记忆力下降"},
            {"key": "irritability", "label": "烦躁"},
            {"key": "fatigue", "label": "疲劳"},
            {"key": "sleepiness", "label": "犯困"},
            {"key": "easy_irritated", "label": "易激惹"},
            {"key": "initiative_decline", "label": "做事主动性下降"},
            {"key": "sleep_dissatisfaction", "label": "对睡眠状况不满意"},
            {"key": "behavior_disorder", "label": "行为紊乱"},
        ],
    },
    {
        "key": "personalityTraits",
        "label": "性格特点",
        "multiple": True,
        "answers": [
            {"key": "extroversion", "label": "外向"},
            {"key": "introversion", "label": "内向"},
            {"key": "sensitive", "label": "敏感"},
            {"key": "suppressed_emotion", "label": "压抑"},
            {"key": "perfectionism", "label": "追求完美"},
            {"key": "overthinking", "label": "思虑过多"},
            {"key": "strict", "label": "较真"},
            {"key": "competitive", "label": "要强"},
        ],
    },
    {
        "key": "sleepFactors",
        "label": "睡眠不好相关因素",
        "multiple": True,
        "answers": [
            {"key": "lifelong_poor_sleep", "label": "从小就睡眠不好"},
            {"key": "parent_poor_sleep", "label": "父亲或母亲睡眠不好"},
            {"key": "daytime_nap", "label": "白天睡太多"},
            {"key": "low_activity", "label": "白天活动不足"},
            {"key": "lying_too_long", "label": "白天躺着时间太久"},
            {"key": "late_caffeine", "label": "过晚饮用含咖啡因饮料"},
            {"key": "excess_caffeine", "label": "过多饮用含咖啡因饮料"},
            {"key": "bed_partner_mismatch", "label": "与床伴睡眠时间不同步"},
            {"key": "social_work_stress_schedule", "label": "社会/工作压力导致不良睡眠时间表"},
            {"key": "sleep_environment_change", "label": "睡眠环境变化"},
            {"key": "shift_work", "label": "倒班"},
            {"key": "overtime", "label": "熬夜加班"},
            {"key": "holiday_irregular", "label": "假期作息不规律"},
            {"key": "changed_sleep_environment", "label": "更换睡眠环境"},
            {"key": "late_wake", "label": "早上赖床"},
            {"key": "too_early_bed", "label": "太早上床"},
            {"key": "irregular_sleep_time", "label": "睡眠时间不规律"},
            {"key": "insufficient_light", "label": "缺乏规律光照"},
            {"key": "irregular_diet", "label": "饮食不规律"},
            {"key": "life_stress", "label": "生活压力大"},
            {"key": "bed_non_sleep", "label": "床上做与睡眠无关的事"},
            {"key": "sleep_worry", "label": "过度担心睡眠"},
            {"key": "bedtime_overthinking", "label": "睡前或床上过度担心和思虑"},
            {"key": "noise_light", "label": "强光、噪声等环境干扰"},
            {"key": "clock_watching", "label": "半夜看时间"},
            {"key": "try_hard_sleep", "label": "太用力入睡"},
            {"key": "night_exercise", "label": "晚上剧烈运动"},
        ],
    },
]

CATEGORY_TRIGGER_DEFAULTS = {
    "alcohol": ("drinkingHabit", "yes"),
    "caffeine": ("caffeineHabit", "yes"),
    "phone": ("bedtimeBehaviors", "electronics_before_bed"),
    "stress": ("sleepFactors", "life_stress,social_work_stress_schedule"),
    "irregular_schedule": ("sleepFactors", "irregular_sleep_time,holiday_irregular,shift_work,overtime"),
    "overthinking": ("sleepFactors", "bedtime_overthinking"),
    "sleep_worry": ("sleepFactors", "sleep_worry"),
    "sleep_restriction": ("sleepFactors", "too_early_bed,late_wake,irregular_sleep_time"),
    "night_wake": ("bedtimeBehaviors", "clock_watching"),
    "exercise": ("sleepFactors", "low_activity,night_exercise"),
    "tea": ("teaHabit", "yes"),
    "smoking": ("smokingHabit", "yes"),
    "nap": ("sleepFactors", "daytime_nap,lying_too_long"),
    "stimulus_control": ("sleepFactors", "bed_non_sleep,clock_watching,try_hard_sleep,too_early_bed"),
    "cognitive_restructuring": ("sleepFactors", "bedtime_overthinking,sleep_worry,try_hard_sleep"),
}


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
        "categoryKey": row.get("category_key"),
        "categoryName": row.get("category_name"),
        "title": row["title"],
        "subtitle": row["subtitle"],
        "duration": row["duration"],
        "cover": row["cover_path"],
        "url": row["media_path"],
        "tags": tags,
        "description": row["description"],
        "triggerQuestionKey": row.get("trigger_question_key"),
        "triggerAnswerKey": row.get("trigger_answer_key"),
        "sortOrder": row.get("sort_order"),
        "createdAt": json_value(row.get("created_at")),
        "updatedAt": json_value(row.get("updated_at")),
    }


def progress_item(row: dict[str, Any]) -> dict[str, Any]:
    completed = row.get("completed_at") is not None or int(row.get("progress_percent") or 0) >= 100
    return {
        "contentId": row["content_id"],
        "type": row.get("type"),
        "progressPercent": row.get("progress_percent"),
        "lastPositionSeconds": row.get("last_position_seconds"),
        "completed": completed,
        "completedAt": json_value(row.get("completed_at")),
        "updatedAt": json_value(row.get("updated_at")),
    }


def get_or_create_user_id(cur: Any, external_user_id: str) -> int:
    external_user_id = (external_user_id or APP_USER_ID).strip() or APP_USER_ID
    cur.execute(
        """
        INSERT INTO app_users (external_user_id, nickname)
        VALUES (%s, %s)
        ON DUPLICATE KEY UPDATE external_user_id = VALUES(external_user_id)
        """,
        (external_user_id, external_user_id),
    )
    cur.execute("SELECT id FROM app_users WHERE external_user_id = %s", (external_user_id,))
    row = cur.fetchone()
    if not row:
        raise HTTPException(status_code=500, detail="user not found")
    return int(row["id"])


def require_admin_token(token: str | None) -> None:
    if ADMIN_TOKEN and token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="invalid admin token")


def slugify(value: str, default: str = "content") -> str:
    slug = re.sub(r"[^a-zA-Z0-9_-]+", "-", value.strip()).strip("-").lower()
    return slug or default


def public_media_path(content_type: str, filename: str) -> str:
    return f"{PUBLIC_BASE_PATH}/media/{content_type}s/{filename}"


def safe_local_upload_path(path_value: str | None) -> Path | None:
    if not path_value:
        return None
    if path_value.startswith(f"{PUBLIC_BASE_PATH}/media/"):
        relative = path_value.removeprefix(f"{PUBLIC_BASE_PATH}/media/").lstrip("/")
        candidate = UPLOAD_DIR / relative
    else:
        candidate = Path(path_value)
        if not candidate.is_absolute():
            candidate = UPLOAD_DIR / candidate

    upload_root = UPLOAD_DIR.resolve()
    try:
        resolved = candidate.resolve()
    except OSError:
        return None
    if resolved == upload_root or upload_root not in resolved.parents:
        return None
    return resolved


def unlink_upload_file(path_value: str | None) -> str | None:
    target = safe_local_upload_path(path_value)
    if not target:
        return None
    if target.exists() and target.is_file():
        target.unlink()
        return str(target)
    return None


async def save_upload(upload: UploadFile, content_type: str) -> dict[str, Any]:
    raw = await upload.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty file")
    digest = hashlib.sha256(raw).hexdigest()
    suffix = Path(upload.filename or "upload.bin").suffix.lower()
    stored_name = f"{datetime.utcnow().strftime('%Y%m%d%H%M%S')}_{digest[:16]}{suffix}"
    target_dir = UPLOAD_DIR / f"{content_type}s"
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / stored_name
    target.write_bytes(raw)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO upload_files
                (category, original_name, stored_name, storage_path, mime_type, size_bytes, sha256)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                original_name = VALUES(original_name),
                stored_name = VALUES(stored_name),
                storage_path = VALUES(storage_path),
                mime_type = VALUES(mime_type),
                size_bytes = VALUES(size_bytes)
            """,
            (content_type, upload.filename, stored_name, str(target), upload.content_type, len(raw), digest),
        )
        cur.execute("SELECT id FROM upload_files WHERE sha256 = %s", (digest,))
        row = cur.fetchone()
    return {
        "id": row["id"],
        "storedName": stored_name,
        "storagePath": str(target),
        "publicPath": public_media_path(content_type, stored_name),
        "mimeType": upload.content_type,
        "sizeBytes": len(raw),
        "sha256": digest,
    }


def categories_from_answers(answers: dict[str, Any], explicit_categories: list[str] | None = None) -> list[str]:
    categories: set[str] = set(explicit_categories or [])

    def has_positive(values: list[str]) -> bool:
        return any(item not in {"none", "no", "false", "False", "否", "无", ""} for item in values)

    drinking_values = answer_values(answers.get("drinkingHabit") or answers.get("alcohol") or answers.get("drink"))
    drinking_time_values = answer_values(answers.get("drinkingTimes"))
    if has_positive(drinking_values) or has_positive(drinking_time_values):
        categories.add("alcohol")

    caffeine_values = answer_values(answers.get("caffeineHabit") or answers.get("caffeine"))
    caffeine_time_values = answer_values(answers.get("caffeineTimes"))
    if has_positive(caffeine_values) or has_positive(caffeine_time_values):
        categories.add("caffeine")

    tea_values = answer_values(answers.get("teaHabit") or answers.get("tea"))
    tea_time_values = answer_values(answers.get("teaTimes"))
    if has_positive(tea_values) or has_positive(tea_time_values):
        categories.add("tea")

    smoking_values = answer_values(answers.get("smokingHabit") or answers.get("smoking"))
    smoking_time_values = answer_values(answers.get("smokingTimes"))
    if has_positive(smoking_values) or has_positive(smoking_time_values):
        categories.add("smoking")

    nap = answers.get("napHabit") or answers.get("daytimeNap") or answers.get("nap")
    if has_positive(answer_values(nap)):
        categories.add("nap")

    exercise_values = (
        answer_values(answers.get("exerciseHabit"))
        + answer_values(answers.get("exercise"))
        + answer_values(answers.get("activityLevel"))
        + answer_values(answers.get("exerciseType"))
        + answer_values(answers.get("exerciseProjects"))
    )
    if any(item in {"no", "low", "insufficient", "low_activity", "night_exercise", "exercise_before_bed", "白天活动不足", "运动不足", "晚上剧烈运动"} for item in exercise_values):
        categories.add("exercise")

    bedtime_behaviors = answer_values(answers.get("bedtimeBehaviors"))
    bedtime_behavior_map = {
        "exercise_before_bed": ["exercise"],
        "electronics_before_bed": ["phone"],
        "clock_watching": ["stimulus_control", "night_wake"],
    }
    for behavior in bedtime_behaviors:
        for mapped in bedtime_behavior_map.get(str(behavior), []):
            categories.add(mapped)

    factors = answers.get("sleepFactors") or answers.get("insomniaFactors") or []
    if isinstance(factors, str):
        factors = [factors]
    factor_map = {
        "phone": ["phone"],
        "睡前看手机": ["phone"],
        "stress": ["stress"],
        "生活压力大": ["stress"],
        "life_stress": ["stress"],
        "social_work_stress_schedule": ["stress", "irregular_schedule"],
        "irregular_schedule": ["irregular_schedule"],
        "睡眠时间不规律": ["irregular_schedule"],
        "irregular_sleep_time": ["irregular_schedule"],
        "holiday_irregular": ["irregular_schedule"],
        "shift_work": ["irregular_schedule"],
        "overtime": ["irregular_schedule"],
        "low_activity": ["exercise"],
        "白天活动不足": ["exercise"],
        "night_exercise": ["exercise"],
        "daytime_nap": ["nap"],
        "白天睡太多": ["nap"],
        "lying_too_long": ["nap"],
        "late_caffeine": ["caffeine"],
        "过晚喝咖啡": ["caffeine"],
        "excess_caffeine": ["caffeine"],
        "sleep_worry": ["sleep_worry", "cognitive_restructuring"],
        "过度担心睡眠": ["sleep_worry", "cognitive_restructuring"],
        "overthinking": ["overthinking", "cognitive_restructuring"],
        "睡前过度思虑": ["overthinking", "cognitive_restructuring"],
        "bedtime_overthinking": ["overthinking", "sleep_worry", "cognitive_restructuring"],
        "too_early_bed": ["stimulus_control"],
        "太早上床": ["stimulus_control"],
        "clock_watching": ["stimulus_control"],
        "夜间看时间": ["stimulus_control"],
        "bed_non_sleep": ["stimulus_control"],
        "try_hard_sleep": ["stimulus_control"],
        "太用力入睡": ["stimulus_control"],
        "late_wake": ["sleep_restriction", "stimulus_control"],
        "stimulus_control": ["stimulus_control"],
        "cognitive_restructuring": ["cognitive_restructuring"],
    }
    for factor in factors:
        for mapped in factor_map.get(str(factor), []):
            categories.add(mapped)

    traits = answers.get("personalityTraits") or []
    if isinstance(traits, str):
        traits = [traits]
    if any(str(item) in {"思虑过多", "overthinking", "perfectionism", "sensitive", "strict"} for item in traits):
        categories.add("overthinking")
        categories.add("cognitive_restructuring")

    therapy = answers.get("therapyModule") or []
    if isinstance(therapy, str):
        therapy = [therapy]
    if any(str(item) in {"stimulus_control", "睡眠刺激", "刺激控制", "睡眠习惯"} for item in therapy):
        categories.add("stimulus_control")
    if any(str(item) in {"cognitive_restructuring", "认知重构"} for item in therapy):
        categories.add("cognitive_restructuring")

    return sorted(categories)


def answer_values(raw: Any) -> list[str]:
    if raw is None or raw is False:
        return []
    if isinstance(raw, (list, tuple, set)):
        return [str(item) for item in raw if item not in {None, "", False}]
    if isinstance(raw, str) and "," in raw:
        return [item.strip() for item in raw.split(",") if item.strip()]
    return [str(raw)] if str(raw) else []


def parse_answer_keys(trigger_answer_key: str = "", trigger_answer_keys: str = "") -> list[str]:
    values: list[str] = []
    if trigger_answer_keys.strip():
        try:
            parsed = json.loads(trigger_answer_keys)
            values.extend(answer_values(parsed))
        except json.JSONDecodeError:
            values.extend(answer_values(trigger_answer_keys))
    values.extend(answer_values(trigger_answer_key))

    deduped: list[str] = []
    for value in values:
        value = value.strip()
        if value and value not in deduped:
            deduped.append(value)
    return deduped


def rule_categories_from_answers(answers: dict[str, Any]) -> list[str]:
    pairs: list[tuple[str, str]] = []
    for question_key, raw in answers.items():
        for answer_key in answer_values(raw):
            pairs.append((str(question_key), answer_key))
    if not pairs:
        return []

    clauses = " OR ".join(["(question_key = %s AND answer_key = %s)"] * len(pairs))
    params: list[str] = []
    for pair in pairs:
        params.extend(pair)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT DISTINCT category_key, sort_order
            FROM survey_content_rules
            WHERE status = 'active' AND ({clauses})
            ORDER BY sort_order ASC, category_key ASC
            """,
            params,
        )
        rows = cur.fetchall()
    return [row["category_key"] for row in rows]


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


@app.get("/v1/content-categories")
def list_content_categories() -> JSONResponse:
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT category_key, name, description, survey_question_key, survey_answer_key, sort_order
            FROM content_categories
            WHERE status = 'active'
            ORDER BY sort_order ASC, id ASC
            """,
        )
        rows = cur.fetchall()
    for row in rows:
        defaults = CATEGORY_TRIGGER_DEFAULTS.get(row.get("category_key"))
        if defaults:
            row["survey_question_key"] = defaults[0]
            row["survey_answer_key"] = defaults[1]
    return JSONResponse(
        {"data": rows},
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


@app.get("/v1/survey/options")
def list_survey_options() -> JSONResponse:
    return JSONResponse(
        {"data": SURVEY_TRIGGER_OPTIONS},
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


@app.get("/v1/health-contents")
def list_health_contents(
    content_type: str | None = None,
    category_key: str | None = None,
    uploaded_only: bool = False,
) -> dict[str, Any]:
    sql = """
        SELECT hc.*, cc.name AS category_name
        FROM health_contents hc
        LEFT JOIN content_categories cc ON cc.category_key = hc.category_key
        WHERE hc.status = 'active'
    """
    params: list[Any] = []
    if content_type:
        sql += " AND hc.type = %s"
        params.append(content_type)
    if category_key:
        sql += " AND hc.category_key = %s"
        params.append(category_key)
    if uploaded_only:
        sql += " AND COALESCE(hc.storage_path, '') <> ''"
    sql += " ORDER BY hc.sort_order ASC, hc.id ASC"
    with db() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    return {"data": [content_item(row) for row in rows]}


@app.get("/v1/content-progress")
def list_content_progress(
    user_id: str = Query(APP_USER_ID, min_length=1),
    completed_only: bool = True,
) -> dict[str, Any]:
    sql = """
        SELECT hc.content_id, hc.type, cp.progress_percent, cp.last_position_seconds,
               cp.completed_at, cp.updated_at
        FROM content_progress cp
        JOIN app_users u ON u.id = cp.user_id
        JOIN health_contents hc ON hc.id = cp.content_id
        WHERE u.external_user_id = %s
          AND hc.status = 'active'
    """
    if completed_only:
        sql += " AND (cp.completed_at IS NOT NULL OR cp.progress_percent >= 100)"
    sql += " ORDER BY cp.updated_at DESC, cp.id DESC"
    with db() as conn, conn.cursor() as cur:
        cur.execute(sql, (user_id,))
        rows = cur.fetchall()
    items = [progress_item(row) for row in rows]
    watched_ids = [item["contentId"] for item in items if item["completed"]]
    return {
        "data": {
            "userId": user_id,
            "watchedContentIds": watched_ids,
            "items": items,
        }
    }


@app.post("/v1/content-progress")
def update_content_progress(payload: ContentProgressRequest) -> dict[str, Any]:
    content_ids = [item.strip() for item in payload.contentIds if item and item.strip()]
    if not content_ids:
        raise HTTPException(status_code=400, detail="contentIds is required")
    progress_percent = max(0, min(100, int(payload.progressPercent)))
    completed_at = datetime.utcnow() if payload.completed or progress_percent >= 100 else None
    placeholders = ",".join(["%s"] * len(content_ids))
    with db() as conn, conn.cursor() as cur:
        user_pk = get_or_create_user_id(cur, payload.userId)
        cur.execute(
            f"""
            SELECT id, content_id
            FROM health_contents
            WHERE status = 'active'
              AND content_id IN ({placeholders})
            """,
            content_ids,
        )
        rows = cur.fetchall()
        found_ids = {row["content_id"] for row in rows}
        missing_ids = [item for item in content_ids if item not in found_ids]
        for row in rows:
            cur.execute(
                """
                INSERT INTO content_progress
                    (user_id, content_id, progress_percent, last_position_seconds, completed_at)
                VALUES (%s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    progress_percent = GREATEST(progress_percent, VALUES(progress_percent)),
                    last_position_seconds = VALUES(last_position_seconds),
                    completed_at = COALESCE(completed_at, VALUES(completed_at)),
                    updated_at = CURRENT_TIMESTAMP
                """,
                (
                    user_pk,
                    row["id"],
                    progress_percent,
                    max(0, int(payload.lastPositionSeconds)),
                    completed_at,
                ),
            )
    return {
        "data": {
            "userId": payload.userId,
            "contentIds": sorted(found_ids),
            "missingContentIds": missing_ids,
            "progressPercent": progress_percent,
            "completed": payload.completed or progress_percent >= 100,
        }
    }


@app.post("/v1/survey/recommendations")
def survey_recommendations(payload: SurveyRecommendationRequest) -> dict[str, Any]:
    categories_set = set(categories_from_answers(payload.answers, payload.categories))
    categories_set.update(rule_categories_from_answers(payload.answers))
    categories = sorted(categories_set)
    if not categories:
        return {"data": {"triggerCategories": [], "autoPlayVideo": None, "comicsToSend": [], "contents": []}}
    placeholders = ",".join(["%s"] * len(categories))
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT hc.*, cc.name AS category_name
            FROM health_contents hc
            LEFT JOIN content_categories cc ON cc.category_key = hc.category_key
            WHERE hc.status = 'active'
              AND hc.category_key IN ({placeholders})
            ORDER BY FIELD(hc.category_key, {placeholders}), hc.type DESC, hc.sort_order ASC, hc.id ASC
            """,
            categories + categories,
        )
        rows = [content_item(row) for row in cur.fetchall()]
    videos = [item for item in rows if item["type"] == "video"]
    comics = [item for item in rows if item["type"] == "comic"]
    return {
        "data": {
            "triggerCategories": categories,
            "autoPlayVideo": videos[0] if videos else None,
            "comicsToSend": comics,
            "contents": rows,
        }
    }


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


@app.post("/v1/contents/upload")
async def upload_content(
    x_admin_token: str | None = Header(default=None),
    content_type: str = Form(...),
    category_key: str = Form(...),
    title: str = Form(...),
    subtitle: str = Form(""),
    duration: str = Form(""),
    description: str = Form(""),
    tags: str = Form(""),
    trigger_question_key: str = Form(""),
    trigger_answer_key: str = Form(""),
    trigger_answer_keys: str = Form(""),
    sort_order: int = Form(100),
    file: UploadFile = File(...),
    cover: UploadFile | None = File(default=None),
) -> dict[str, Any]:
    require_admin_token(x_admin_token)
    content_type = content_type.lower().strip()
    if content_type not in {"video", "comic"}:
        raise HTTPException(status_code=400, detail="content_type must be video or comic")
    tags_list = [item.strip() for item in tags.split(",") if item.strip()]
    answer_keys = parse_answer_keys(trigger_answer_key, trigger_answer_keys)
    trigger_answer_value = ",".join(answer_keys)
    saved = await save_upload(file, content_type)
    cover_path = ""
    if cover and cover.filename:
        cover_saved = await save_upload(cover, "cover")
        cover_path = cover_saved["publicPath"]
    content_id = f"{slugify(category_key)}-{content_type}-{saved['sha256'][:12]}"
    media_path = saved["publicPath"]
    subtitle = subtitle or ("睡眠卫生教育" if content_type == "video" else "漫画")
    duration = duration or ("待标注" if content_type == "video" else "待标注")
    description = description or title
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO health_contents (
                content_id, type, category_key, trigger_question_key, trigger_answer_key,
                title, subtitle, duration, cover_path, media_path, tags_json, description,
                storage_path, file_size_bytes, mime_type, sha256, status, sort_order
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'active', %s)
            ON DUPLICATE KEY UPDATE
                category_key = VALUES(category_key),
                trigger_question_key = VALUES(trigger_question_key),
                trigger_answer_key = VALUES(trigger_answer_key),
                title = VALUES(title),
                subtitle = VALUES(subtitle),
                duration = VALUES(duration),
                cover_path = VALUES(cover_path),
                media_path = VALUES(media_path),
                tags_json = VALUES(tags_json),
                description = VALUES(description),
                storage_path = VALUES(storage_path),
                file_size_bytes = VALUES(file_size_bytes),
                mime_type = VALUES(mime_type),
                sha256 = VALUES(sha256),
                status = 'active',
                sort_order = VALUES(sort_order)
            """,
            (
                content_id,
                content_type,
                category_key,
                trigger_question_key,
                trigger_answer_value,
                title,
                subtitle,
                duration,
                cover_path,
                media_path,
                json.dumps(tags_list, ensure_ascii=False),
                description,
                saved["storagePath"],
                saved["sizeBytes"],
                saved["mimeType"],
                saved["sha256"],
                sort_order,
            ),
        )
        if trigger_question_key and answer_keys:
            for index, answer_key in enumerate(answer_keys):
                rule_key = ".".join(
                    [
                        "upload",
                        slugify(trigger_question_key, "question"),
                        slugify(answer_key, "answer"),
                        slugify(category_key, "category"),
                    ]
                )[:96]
                cur.execute(
                    """
                    INSERT INTO survey_content_rules
                        (rule_key, survey_key, question_key, answer_key, category_key, action, sort_order)
                    VALUES (%s, 'upload', %s, %s, %s, 'autoplay_video_and_send_comic', %s)
                    ON DUPLICATE KEY UPDATE
                        survey_key = VALUES(survey_key),
                        question_key = VALUES(question_key),
                        answer_key = VALUES(answer_key),
                        category_key = VALUES(category_key),
                        action = VALUES(action),
                        status = 'active',
                        sort_order = VALUES(sort_order)
                    """,
                    (rule_key, trigger_question_key, answer_key, category_key, sort_order + index),
                )
        cur.execute(
            """
            SELECT hc.*, cc.name AS category_name
            FROM health_contents hc
            LEFT JOIN content_categories cc ON cc.category_key = hc.category_key
            WHERE hc.content_id = %s
            """,
            (content_id,),
        )
        row = cur.fetchone()
    return {"data": content_item(row)}


@app.delete("/v1/health-contents/{content_id}")
def delete_health_content(
    content_id: str,
    x_admin_token: str | None = Header(default=None),
) -> dict[str, Any]:
    require_admin_token(x_admin_token)
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT *
            FROM health_contents
            WHERE content_id = %s AND status = 'active'
            """,
            (content_id,),
        )
        row = cur.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="content not found")

        media_storage_path = row.get("storage_path")
        media_sha = row.get("sha256")
        cover_path = row.get("cover_path")
        deleted_files: list[str] = []

        cur.execute(
            """
            SELECT COUNT(*) AS count
            FROM health_contents
            WHERE status = 'active'
              AND content_id <> %s
              AND storage_path = %s
            """,
            (content_id, media_storage_path),
        )
        if not cur.fetchone()["count"]:
            deleted = unlink_upload_file(media_storage_path)
            if deleted:
                deleted_files.append(deleted)

        if cover_path:
            cur.execute(
                """
                SELECT COUNT(*) AS count
                FROM health_contents
                WHERE status = 'active'
                  AND content_id <> %s
                  AND cover_path = %s
                """,
                (content_id, cover_path),
            )
            if not cur.fetchone()["count"]:
                deleted = unlink_upload_file(cover_path)
                if deleted:
                    deleted_files.append(deleted)

        cur.execute(
            """
            UPDATE health_contents
            SET status = 'deleted'
            WHERE content_id = %s
            """,
            (content_id,),
        )

        if media_sha:
            cur.execute(
                """
                SELECT COUNT(*) AS count
                FROM health_contents
                WHERE status = 'active' AND sha256 = %s
                """,
                (media_sha,),
            )
            if not cur.fetchone()["count"]:
                cur.execute("DELETE FROM upload_files WHERE sha256 = %s", (media_sha,))

    return {
        "data": {
            "id": content_id,
            "status": "deleted",
            "deletedFiles": deleted_files,
        }
    }


@app.get("/tools/upload", response_class=HTMLResponse)
def upload_tool() -> HTMLResponse:
    if not UPLOAD_TOOL_PATH.exists():
        raise HTTPException(status_code=404, detail="upload tool not found")
    return HTMLResponse(
        UPLOAD_TOOL_PATH.read_text(encoding="utf-8"),
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )
