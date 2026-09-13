from typing import AsyncIterator, Optional

from openai import AsyncOpenAI

from .base import LLMProvider


class OpenAIProvider(LLMProvider):
    name = "openai"

    def __init__(self, default_model: str = "gpt-4o"):
        self.default_model = default_model

    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        client = AsyncOpenAI(api_key=api_key)

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
