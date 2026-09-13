# wisp

Open-source, cross-platform real-time AI overlay assistant. Full spec in [`docs/PRD.md`](docs/PRD.md).

**Status:** v0.1 scaffold — core hotkey → screenshot → LLM → streamed overlay loop wired end to end. Audio transcription is a runnable stub, not yet integrated into the main dev loop. See `docs/progress.md`.

## What works right now

- Electron overlay window: transparent, always-on-top, draggable, excluded from screen capture on macOS/Windows (`setContentProtection`)
- `Cmd/Ctrl + Enter` → captures primary screen → sends to backend `/capture` → streams LLM response into the overlay
- `Cmd/Ctrl + \` → toggle overlay visibility
- Multi-provider backend (Anthropic implemented fully; OpenAI and Ollama stubbed with the same interface)
- Rolling transcript context buffer (ready to receive chunks from the whisper stub via `/transcript`)

## What's NOT wired up yet

- Whisper transcription loop runs standalone but isn't auto-started with the app
- System audio loopback (hearing the other call participant) — not implemented, see PRD §8
- Settings UI (API key entry, provider switching, mic device selection) — currently config is via `.env`
- Linux screen-capture exclusion — not implemented (X11 has no equivalent; Wayland untested). Overlay works fine, just won't be hidden from screen share on Linux.
- Packaging/installers — `electron-builder` config exists in `package.json` but is untested

## Setup

### Frontend (Electron)

```bash
npm install
cp .env.example .env   # then fill in your API key(s)
npm run dev
```

### Backend (Python)

```bash
cd backend
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp ../.env.example ../.env  # if not already done
python main.py
```

Backend runs on `http://127.0.0.1:8137`. Start it before `npm run dev`, or the overlay will just show "thinking..." forever on capture.

### Optional: local transcription

```bash
cd backend
python -m transcription.whisper_stream
```

Requires a working mic input device. First run downloads the whisper model (`base.en` by default, edit in `whisper_stream.py` to change size/language).

### Optional: local LLM (Ollama)

```bash
ollama serve
ollama pull llava
```

Then set `WISP_PROVIDER=ollama` in `.env`.

## Project structure

See `docs/PRD.md` §6.2 for the full layout rationale.

```
src/main/       Electron main process (window, hotkeys, screenshot capture)
src/renderer/   React overlay UI
backend/        FastAPI service (providers, context buffer, transcription)
playbooks/      System prompt presets (JSON)
docs/           PRD, progress log, memory/decisions log
```

## For Claude Code / contributors

Read `CLAUDE.md` first, then `docs/progress.md` and `docs/memory.md`.

## License

MIT — see `LICENSE`.
