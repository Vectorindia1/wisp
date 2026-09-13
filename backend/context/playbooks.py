import json
from pathlib import Path

PLAYBOOKS_DIR = Path(__file__).resolve().parent.parent.parent / "playbooks"

DEFAULT_PLAYBOOK = "general"


def load_playbook(name: str = DEFAULT_PLAYBOOK) -> str:
    path = PLAYBOOKS_DIR / f"{name}.json"
    if not path.exists():
        path = PLAYBOOKS_DIR / f"{DEFAULT_PLAYBOOK}.json"
    data = json.loads(path.read_text())
    return data["system_prompt"]
