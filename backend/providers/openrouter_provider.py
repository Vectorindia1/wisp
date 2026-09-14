import os
from typing import AsyncIterator, Optional

from openai import AsyncOpenAI

from .base import LLMProvider

OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"


class OpenRouterProvider(LLMProvider):
    """
    OpenRouter exposes an OpenAI-compatible chat/completions API in front of
    many underlying models (Anthropic, OpenAI, Google, Meta, etc. -- one key,
    many models), so this reuses the `openai` SDK already in requirements.txt
    with a different base_url rather than adding a new dependency.

    Model choice is a single env var for now (no model picker in Settings
    yet) -- default is a cheap, vision-capable option; override with
    WISP_OPENROUTER_MODEL for anything else OpenRouter serves, e.g.
    "anthropic/claude-3.5-sonnet" or "google/gemini-flash-1.5".
    """

    name = "openrouter"

    def __init__(self, default_model: Optional[str] = None):
        self.default_model = default_model or os.getenv("WISP_OPENROUTER_MODEL", "openai/gpt-4o-mini")

    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        client = AsyncOpenAI(api_key=api_key, base_url=OPENROUTER_BASE_URL)

        user_text = (
            f"Recent conversation transcript:\n{transcript_context}\n\n"
            "Here is the current screen. Respond with the most useful, "
            "concise assistance for what's happening right now."
        )

        stream = await client.chat.completions.create(
            model=self.default_model,
            stream=True,
            messages=[
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user_text},
                        {"type": "image_url", "image_url": {"url": image_data_url}},
                    ],
                },
            ],
        )
        async for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                yield delta
