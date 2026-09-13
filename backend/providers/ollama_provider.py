import json
from typing import AsyncIterator, Optional

import httpx

from .base import LLMProvider


class OllamaProvider(LLMProvider):
    """
    Local-only provider. Requires Ollama running (`ollama serve`) with a
    vision-capable model pulled, e.g. `ollama pull llava`.
    """

    name = "ollama"

    def __init__(self, model: str = "llava", base_url: str = "http://127.0.0.1:11434"):
        self.model = model
        self.base_url = base_url

    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        _, b64data = image_data_url.split(",", 1)

        user_text = (
            f"{system_prompt}\n\nRecent transcript:\n{transcript_context}\n\n"
            "Respond with the most useful, concise assistance for what's happening right now."
        )

        payload = {
            "model": self.model,
            "prompt": user_text,
            "images": [b64data],
            "stream": True,
        }

        async with httpx.AsyncClient(timeout=None) as client:
            async with client.stream("POST", f"{self.base_url}/api/generate", json=payload) as resp:
                async for line in resp.aiter_lines():
                    if not line:
                        continue
                    data = json.loads(line)
                    if data.get("response"):
                        yield data["response"]
                    if data.get("done"):
                        break
