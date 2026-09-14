import asyncio
import queue
from typing import AsyncIterator, Optional

import google.generativeai as genai

from .base import LLMProvider

_SENTINEL = object()


class GeminiProvider(LLMProvider):
    """
    google-generativeai's streaming call is synchronous/blocking (no native
    asyncio support as of 0.4.x). We run it on a worker thread and relay
    chunks back through a thread-safe queue so it behaves like every other
    provider's async generator without blocking the event loop.
    """

    name = "gemini"

    def __init__(self, default_model: str = "gemini-1.5-flash"):
        self.default_model = default_model

    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel(self.default_model, system_instruction=system_prompt)

        header, b64data = image_data_url.split(",", 1)
        mime_type = header.split(";")[0].split(":")[1]

        user_text = (
            f"Recent conversation transcript:\n{transcript_context}\n\n"
            "Here is the current screen. Respond with the most useful, "
            "concise assistance for what's happening right now."
        )

        import base64

        image_part = {"mime_type": mime_type, "data": base64.b64decode(b64data)}

        chunk_queue: "queue.Queue[object]" = queue.Queue()
        loop = asyncio.get_event_loop()

        def worker():
            try:
                response = model.generate_content([image_part, user_text], stream=True)
                for chunk in response:
                    if chunk.text:
                        chunk_queue.put(chunk.text)
            except Exception as e:  # surfaced to the overlay instead of swallowed
                chunk_queue.put(f"[gemini error] {e}")
            finally:
                chunk_queue.put(_SENTINEL)

        # Fire the blocking call on a worker thread WITHOUT awaiting it here --
        # awaiting would block this coroutine until the whole response is
        # done, defeating streaming. Drain the queue concurrently instead,
        # using run_in_executor for each blocking .get() so we never block
        # the event loop while waiting on the next chunk either.
        loop.run_in_executor(None, worker)

        while True:
            item = await loop.run_in_executor(None, chunk_queue.get)
            if item is _SENTINEL:
                break
            yield item
