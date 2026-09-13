"""
Continuous mic transcription loop. Runs as a background task, chunks mic
audio, runs it through faster-whisper locally, and POSTs recognized text to
the /transcript endpoint so it lands in the rolling context buffer.

This is a stub/reference implementation -- run it as a separate process
during development (`python -m backend.transcription.whisper_stream`), or
wire it into main.py's startup event once you've tuned chunk size/latency
for your machine.

NOTE: this captures MIC input only. Capturing system audio (to hear the
other call participant) requires a platform-specific loopback source -- see
docs/PRD.md §8 (BlackHole on macOS, WASAPI loopback on Windows, PulseAudio/
PipeWire monitor on Linux). Not implemented here; that's Phase 3 in the PRD.
"""
import queue
import sys
import time

import numpy as np
import requests
import sounddevice as sd
from faster_whisper import WhisperModel

BACKEND_URL = "http://127.0.0.1:8137/transcript"
SAMPLE_RATE = 16000
CHUNK_SECONDS = 4  # trade-off: shorter = lower latency, worse accuracy per chunk

audio_queue: "queue.Queue[np.ndarray]" = queue.Queue()


def audio_callback(indata, frames, time_info, status):
    if status:
        print(status, file=sys.stderr)
    audio_queue.put(indata.copy())


def run(model_size: str = "base.en"):
    print(f"Loading whisper model '{model_size}'...")
    model = WhisperModel(model_size, device="cpu", compute_type="int8")

    with sd.InputStream(
        samplerate=SAMPLE_RATE, channels=1, callback=audio_callback, dtype="float32"
    ):
        print("Listening... (Ctrl+C to stop)")
        buffer = np.zeros((0,), dtype="float32")

        while True:
            chunk = audio_queue.get()
            buffer = np.concatenate([buffer, chunk.flatten()])

            if len(buffer) >= SAMPLE_RATE * CHUNK_SECONDS:
                segments, _ = model.transcribe(buffer, language="en")
                text = " ".join(seg.text.strip() for seg in segments).strip()
                buffer = np.zeros((0,), dtype="float32")

                if text:
                    try:
                        requests.post(BACKEND_URL, json={"text": text}, timeout=2)
                        print(f"[transcript] {text}")
                    except requests.RequestException as e:
                        print(f"Failed to post transcript chunk: {e}", file=sys.stderr)


if __name__ == "__main__":
    # Optional model-size arg, e.g. `python whisper_stream.py small.en`.
    # Passed by backend/main.py's subprocess launcher via WISP_WHISPER_MODEL.
    model_arg = sys.argv[1] if len(sys.argv) > 1 else "base.en"
    run(model_size=model_arg)
