import base64
from typing import AsyncIterator, Optional

from anthropic import AsyncAnthropic

from .base import LLMProvider


class AnthropicProvider(LLMProvider):
    name = "anthropic"

    def __init__(self, default_model: str = "claude-sonnet-4-6"):
        self.default_model = default_model

    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        client = AsyncAnthropic(api_key=api_key)

        header, b64data = image_data_url.split(",", 1)
        media_type = header.split(";")[0].split(":")[1]  # e.g. image/png

        user_text = (
            f"Recent conversation transcript:\n{transcript_context}\n\n"
            "Here is the current screen. Respond with the most useful, "
            "concise assistance for what's happening right now."
            if transcript_context
            else "Here is the current screen. Respond with the most useful, "
            "concise assistance for what's happening right now."
        )

        async with client.messages.stream(
            model=self.default_model,
            max_tokens=1024,
            system=system_prompt,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": media_type,
                                "data": b64data,
                            },
                        },
                        {"type": "text", "text": user_text},
                    ],
                }
            ],
        ) as stream:
            async for text in stream.text_stream:
                yield text
