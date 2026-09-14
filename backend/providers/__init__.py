import os

from .anthropic_provider import AnthropicProvider
from .base import LLMProvider
from .gemini_provider import GeminiProvider
from .ollama_provider import OllamaProvider
from .openai_provider import OpenAIProvider
from .openrouter_provider import OpenRouterProvider

REGISTRY: dict[str, LLMProvider] = {
    "anthropic": AnthropicProvider(),
    "openai": OpenAIProvider(),
    "gemini": GeminiProvider(),
    "openrouter": OpenRouterProvider(),
    "ollama": OllamaProvider(base_url=os.getenv("WISP_OLLAMA_BASE_URL", "http://127.0.0.1:11434")),
}


def get_provider(name: str) -> LLMProvider:
    if name not in REGISTRY:
        raise ValueError(f"Unknown provider '{name}'. Available: {list(REGISTRY)}")
    return REGISTRY[name]
