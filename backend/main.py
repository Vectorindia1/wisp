import atexit
import os
import subprocess
import sys
from contextlib import asynccontextmanager
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

import db
from context.buffer import RollingTranscriptBuffer
from context.playbooks import load_playbook
from providers import get_provider

load_dotenv()

WHISPER_ENABLED = os.getenv("WISP_WHISPER_ENABLED", "true").lower() != "false"
WHISPER_MODEL = os.getenv("WISP_WHISPER_MODEL", "base.en")
DEFAULT_PROVIDER = os.getenv("WISP_PROVIDER", "anthropic")
DEFAULT_PLAYBOOK = os.getenv("WISP_PLAYBOOK", "general")

_transcription_process: subprocess.Popen | None = None
_session_id: int | None = None


def _start_transcription_subprocess():
    """
    Spawns backend/transcription/whisper_stream.py as a separate OS process
    rather than an asyncio task. The whisper loop is fully synchronous
    (blocking sounddevice callback + CPU-bound model.transcribe) -- running
    it in-process would stall the FastAPI event loop and delay /capture
    responses. It talks back to us over HTTP (POST /transcript), same as if
    a developer ran it manually, so no other wiring is needed here.
    """
    global _transcription_process
    if not WHISPER_ENABLED:
        print("[wisp] Whisper transcription disabled (WISP_WHISPER_ENABLED=false)")
        return
    script = Path(__file__).parent / "transcription" / "whisper_stream.py"
    try:
        _transcription_process = subprocess.Popen(
            [sys.executable, str(script), WHISPER_MODEL],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        print(f"[wisp] Started transcription subprocess (pid={_transcription_process.pid}, model={WHISPER_MODEL})")
    except Exception as e:
        # Non-fatal: the capture loop (screenshot -> LLM) works fine without
        # audio context, just with an empty transcript_context.
        print(f"[wisp] Failed to start transcription subprocess: {e}. "
              f"Continuing without audio context.", file=sys.stderr)


def _stop_transcription_subprocess():
    global _transcription_process
    if _transcription_process and _transcription_process.poll() is None:
        _transcription_process.terminate()
        try:
            _transcription_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            _transcription_process.kill()
        print("[wisp] Stopped transcription subprocess")


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _session_id
    _session_id = db.start_session(playbook=DEFAULT_PLAYBOOK)
    _start_transcription_subprocess()
    yield
    _stop_transcription_subprocess()


app = FastAPI(title="wisp-backend", lifespan=lifespan)
atexit.register(_stop_transcription_subprocess)

# Backend only ever talks to the local Electron shell -- CORS is wide open
# here because it's bound to 127.0.0.1 only (see __main__ below), not because
# this should ever be exposed on a network interface.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

transcript_buffer = RollingTranscriptBuffer(window_seconds=120)


class CaptureRequest(BaseModel):
    image: str  # base64 data URL from Electron desktopCapturer
    provider: str | None = None
    playbook: str | None = None


class TranscriptChunkIn(BaseModel):
    text: str


@app.post("/capture")
async def capture(req: CaptureRequest):
    provider_name = req.provider or DEFAULT_PROVIDER
    playbook_name = req.playbook or DEFAULT_PLAYBOOK
    provider = get_provider(provider_name)
    system_prompt = load_playbook(playbook_name)
    context = transcript_buffer.get_context()

    async def stream():
        full_response = ""
        try:
            async for chunk in provider.stream_vision_response(
                image_data_url=req.image,
                transcript_context=context,
                system_prompt=system_prompt,
                api_key=os.getenv(f"{provider_name.upper()}_API_KEY"),
            ):
                full_response += chunk
                yield chunk
        except Exception as e:
            # Provider SDKs raise their own exception types (AuthenticationError,
            # RateLimitError, connection errors, ...) well after headers are
            # already sent for a StreamingResponse, so we can't turn this into
            # an HTTP error status -- surface it as visible text in the overlay
            # instead of letting the connection die with no explanation.
            error_text = f"\n\n[wisp error] {provider_name} request failed: {e}"
            full_response += error_text
            yield error_text
            return
        finally:
            # Persist whatever we got (full response, or partial + error text)
            # so a client disconnect or provider failure doesn't leave a
            # half-written row; text-only (no screenshot bytes), matching the
            # local-only/no-images-on-disk policy in docs/PRD.md §6.4.
            if _session_id is not None:
                db.save_capture(
                    session_id=_session_id,
                    prompt_context=context,
                    response=full_response,
                    provider=provider_name,
                )

    return StreamingResponse(stream(), media_type="text/plain")


@app.post("/transcript")
async def add_transcript_chunk(chunk: TranscriptChunkIn):
    """Called by the local whisper transcription loop as new speech is recognized."""
    transcript_buffer.add(chunk.text)
    if _session_id is not None:
        db.save_transcript_chunk(session_id=_session_id, text=chunk.text)
    return {"ok": True}


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    # Bound to loopback only -- this backend should never be reachable from
    # the network. Port matches WISP_BACKEND_URL default in the Electron app.
    uvicorn.run(app, host="127.0.0.1", port=8137)
