from .anthropic_provider import AnthropicProvider
from .base import LLMProvider
from .ollama_provider import OllamaProvider
from .openai_provider import OpenAIProvider

REGISTRY: dict[str, LLMProvider] = {
    "anthropic": AnthropicProvider(),
    "openai": OpenAIProvider(),
    "ollama": OllamaProvider(),
}


def get_provider(name: str) -> LLMProvider:
    if name not in REGISTRY:
        raise ValueError(f"Unknown provider '{name}'. Available: {list(REGISTRY)}")
    return REGISTRY[name]
