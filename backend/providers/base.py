"""
Base interface every LLM provider adapter must implement.

Keeping this thin and identical across providers is what lets the rest of the
backend (main.py, context/) stay provider-agnostic. If you add a provider,
implement `stream_vision_response` and register it in `providers/__init__.py`.
"""
from abc import ABC, abstractmethod
from typing import AsyncIterator, Optional


class LLMProvider(ABC):
    name: str

    @abstractmethod
    async def stream_vision_response(
        self,
        image_data_url: str,
        transcript_context: str,
        system_prompt: str,
        api_key: Optional[str] = None,
    ) -> AsyncIterator[str]:
        """
        Yields response text chunks as they arrive from the model.
        `image_data_url` is a base64 data URL (e.g. 'data:image/png;base64,...').
        `transcript_context` is the rolling audio transcript buffer (may be empty).
        """
        raise NotImplementedError
