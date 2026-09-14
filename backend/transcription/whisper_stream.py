"""
Continuous mic transcription loop -- chunks mic audio, runs it through
faster-whisper locally, and hands recognized text off via an `on_transcript`
callback so it lands in the rolling context buffer.

Two ways to run this:

1. In-process, on a background thread (how backend/main.py uses it): import
   `run()` and pass `on_transcript=transcript_buffer.add` directly, plus a
   `threading.Event` to stop it on shutdown. This is the only option that
   works inside the PyInstaller-frozen backend -- `sys.executable` there
   points at the frozen backend binary itself, not a real Python
   interpreter, so spawning `[sys.executable, "whisper_stream.py"]` as a
   *subprocess* (an earlier approach) silently fails in every packaged
   build. See docs/memory.md.

2. Standalone script during development (`python -m
   backend.transcription.whisper_stream`), which POSTs to a locally running
   backend's /transcript endpoint instead -- useful for iterating on
   chunk-size/latency without restarting the whole app.

NOTE: this captures MIC input only. Capturing system audio (to hear the
other call participant) requires a platform-specific loopback source -- see
docs/PRD.md §8 (BlackHole on macOS, WASAPI loopback on Windows, PulseAudio/
PipeWire monitor on Linux). Not implemented here; that's Phase 3 in the PRD.
"""
import queue
import sys
import threading
from typing import Callable, Optional

import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel

SAMPLE_RATE = 16000
CHUNK_SECONDS = 4  # trade-off: shorter = lower latency, worse accuracy per chunk


def run(
    model_size: str,
    on_transcript: Callable[[str], None],
    stop_event: Optional[threading.Event] = None,
) -> None:
    """Blocks until `stop_event` is set (or forever, if none given -- only
    the CLI entrypoint below relies on that, via Ctrl+C)."""
    stop_event = stop_event or threading.Event()
    audio_queue: "queue.Queue[np.ndarray]" = queue.Queue()

    def audio_callback(indata, frames, time_info, status):
        if status:
            print(status, file=sys.stderr)
        audio_queue.put(indata.copy())

    print(f"[whisper] Loading model '{model_size}'...")
    model = WhisperModel(model_size, device="cpu", compute_type="int8")

    with sd.InputStream(
        samplerate=SAMPLE_RATE, channels=1, callback=audio_callback, dtype="float32"
    ):
        print("[whisper] Listening...")
        buffer = np.zeros((0,), dtype="float32")

        while not stop_event.is_set():
            try:
                chunk = audio_queue.get(timeout=0.5)
            except queue.Empty:
                continue  # loop back around so stop_event actually gets checked
            buffer = np.concatenate([buffer, chunk.flatten()])

            if len(buffer) >= SAMPLE_RATE * CHUNK_SECONDS:
                segments, _ = model.transcribe(buffer, language="en")
                text = " ".join(seg.text.strip() for seg in segments).strip()
                buffer = np.zeros((0,), dtype="float32")

                if text:
                    on_transcript(text)
                    print(f"[whisper] {text}")


if __name__ == "__main__":
    import requests

    BACKEND_URL = "http://127.0.0.1:8137/transcript"

    def post_to_backend(text: str):
        try:
            requests.post(BACKEND_URL, json={"text": text}, timeout=2)
        except requests.RequestException as e:
            print(f"[whisper] Failed to post transcript chunk: {e}", file=sys.stderr)

    # Optional model-size arg, e.g. `python whisper_stream.py small.en`.
    model_arg = sys.argv[1] if len(sys.argv) > 1 else "base.en"
    try:
        run(model_size=model_arg, on_transcript=post_to_backend)
    except KeyboardInterrupt:
        print("\n[whisper] Stopped.")
