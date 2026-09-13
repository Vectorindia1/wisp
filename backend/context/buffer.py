import time
from collections import deque
from dataclasses import dataclass


@dataclass
class TranscriptChunk:
    text: str
    timestamp: float


class RollingTranscriptBuffer:
    """
    Keeps the last N seconds of transcribed audio. Fed by the whisper
    transcription loop; read by the /capture endpoint when bundling context
    for the LLM call.
    """

    def __init__(self, window_seconds: int = 120):
        self.window_seconds = window_seconds
        self._chunks: deque[TranscriptChunk] = deque()

    def add(self, text: str):
        self._chunks.append(TranscriptChunk(text=text, timestamp=time.time()))
        self._evict_old()

    def _evict_old(self):
        cutoff = time.time() - self.window_seconds
        while self._chunks and self._chunks[0].timestamp < cutoff:
            self._chunks.popleft()

    def get_context(self) -> str:
        self._evict_old()
        return " ".join(chunk.text for chunk in self._chunks)

    def clear(self):
        self._chunks.clear()
