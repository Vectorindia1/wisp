import sqlite3
import time
from pathlib import Path

DB_PATH = Path.home() / ".wisp" / "wisp.db"

SCHEMA = """
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at REAL NOT NULL,
    playbook TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS transcript_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    text TEXT NOT NULL,
    timestamp REAL NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE TABLE IF NOT EXISTS captures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    prompt_context TEXT,
    response TEXT,
    provider TEXT,
    timestamp REAL NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);
"""

# Data is local-only by design (see docs/PRD.md §6.4). Screenshots themselves
# are NOT persisted to disk by default -- only text (transcript + LLM
# response) is stored, and only if the user opts to keep session history.
# Auto-rolloff: caller should periodically DELETE rows older than the
# retention window configured in settings.


def get_connection() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.executescript(SCHEMA)
    return conn


def start_session(playbook: str) -> int:
    conn = get_connection()
    cur = conn.execute(
        "INSERT INTO sessions (started_at, playbook) VALUES (?, ?)",
        (time.time(), playbook),
    )
    conn.commit()
    session_id = cur.lastrowid
    conn.close()
    return session_id


def save_transcript_chunk(session_id: int, text: str):
    conn = get_connection()
    conn.execute(
        "INSERT INTO transcript_chunks (session_id, text, timestamp) VALUES (?, ?, ?)",
        (session_id, text, time.time()),
    )
    conn.commit()
    conn.close()


def save_capture(session_id: int, prompt_context: str, response: str, provider: str):
    conn = get_connection()
    conn.execute(
        "INSERT INTO captures (session_id, prompt_context, response, provider, timestamp) "
        "VALUES (?, ?, ?, ?, ?)",
        (session_id, prompt_context, response, provider, time.time()),
    )
    conn.commit()
    conn.close()


def rolloff_old_data(retention_seconds: int):
    cutoff = time.time() - retention_seconds
    conn = get_connection()
    conn.execute("DELETE FROM transcript_chunks WHERE timestamp < ?", (cutoff,))
    conn.execute("DELETE FROM captures WHERE timestamp < ?", (cutoff,))
    conn.commit()
    conn.close()
