# Wisp Build & Run Guide

## Prerequisites

- **Node.js** 18+ (for Electron)
- **Python 3.9+** (for FastAPI backend)
- **Anthropic API key** (or OpenAI/Gemini/Ollama as alternatives)

## Quick Start

### 1. Frontend Dependencies

```bash
npm install
```

### 2. Backend Dependencies (Python venv recommended)

```bash
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r backend/requirements.txt
```

### 3. Environment Setup

```bash
cp .env.example .env
# Edit .env and add your ANTHROPIC_API_KEY
```

### 4. Run in Development

**Terminal 1 — Frontend (with Vite + Electron):**
```bash
npm run dev
```

This starts:
- Vite dev server on http://localhost:5173
- Electron watching TypeScript changes in `src/main/`
- Hot reload on React component changes

**Terminal 2 — Python Backend:**
```bash
source venv/bin/activate
python backend/main.py
```

Runs on `http://127.0.0.1:8137` (loopback-only, as designed).

### 5. Test the Loop

1. App launches with transparent overlay in bottom-right corner
2. Press **⌘⏎** (Ctrl+Enter on Windows/Linux) to capture
3. App takes screenshot → sends to backend → LLM processes + streams response → overlay displays result
4. Press **⌘\\** (Ctrl+\ on Windows/Linux) to hide/show overlay

## Available Scripts

```bash
npm run dev              # Watch + dev server + Electron
npm run dev:vite        # Vite only (port 5173)
npm run dev:electron    # Electron only (watches TypeScript)
npm run build           # Build for distribution
npm run build:backend   # Bundle the Python backend into a standalone exe (PyInstaller)
npm run build:app       # Bundle backend + build + package with electron-builder (current OS)
npm run build:mac       # macOS .dmg (must run ON macOS)
npm run build:win       # Windows .exe (must run ON Windows)
npm run build:linux     # Linux .AppImage/.deb (must run ON Linux)
```

## Producing a distributable installer (.exe / .dmg / .AppImage)

The packaged app bundles the Python backend as a standalone executable via
PyInstaller, so **end users never install Python** — `main.ts` auto-spawns
`resources/backend/wisp-backend(.exe)` on launch and waits for it to become
healthy before showing the overlay. Verified working end-to-end on Linux:
`npm run build:linux` produces a `linux-unpacked/wisp` that, when run, spawns
its own bundled backend with zero manual steps.

**Important: PyInstaller does not cross-compile.** Building on Linux gives
you a Linux backend binary; you cannot produce `wisp-backend.exe` from a
Linux or macOS machine. To ship Windows, the whole `build:win` pipeline
(`build:backend` included) must run **on an actual Windows machine**, and
likewise `build:mac` on macOS.

### Option A — Build on your own Windows machine

```powershell
git clone <this repo> && cd wisp
npm install
python -m venv venv
venv\Scripts\activate
pip install -r backend\requirements.txt pyinstaller
npm run build:win
```

Output: `release\Wisp Setup <version>.exe` (NSIS installer).

### Option B — Let GitHub Actions build it for you (no Windows machine needed)

`.github/workflows/build.yml` builds all 3 platforms in CI, each on its
native OS runner (`windows-latest` produces the real `.exe`). Trigger it by
pushing a tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Or run it on demand from the Actions tab (`workflow_dispatch`). Download the
`wisp-windows-latest` artifact from the completed run — it contains the
`.exe` installer, no local Windows machine required.

## Architecture

- **`src/main/main.ts`**: Electron main process — window management, global hotkeys, screenshot capture
- **`src/renderer/Overlay.tsx`**: React component for the overlay UI — streaming display, styling
- **`backend/main.py`**: FastAPI server — /capture endpoint handles LLM streaming
- **`backend/providers/`**: LLM provider abstractions (Anthropic, OpenAI, Ollama, Gemini)
- **`backend/context/`**: Transcript buffer (rolling window) and playbook loader
- **`backend/transcription/`**: Whisper integration (currently stubbed — needs auto-startup)
- **`playbooks/`**: JSON files with system prompts (general.json is default)

## Known Issues / TODOs

### Phase 0 — Verification (untested in real conditions)
- [ ] `setContentProtection(true)` actually excludes overlay from Zoom/Meet screen share on Windows & macOS
- [ ] Overlay window appears at correct z-order (always-on-top, above fullscreen)
- [ ] Screenshot capture produces usable image data for vision LLM

### Phase 1 — Audio Transcription
- [ ] Auto-start Whisper transcription loop on app launch
- [ ] Feed mic input → Whisper → rolling buffer (currently manual script in `backend/transcription/whisper_stream.py`)
- [ ] Optional: system-audio loopback (hear the call participant) — macOS BlackHole, Windows WASAPI, Linux PulseAudio

### Phase 2 — Settings UI
- [ ] Settings panel: API key input, provider selection, hotkey customization, mic device picker
- [ ] Persist settings to local SQLite
- [ ] Hot-switch LLM providers without restart

### Phase 3 — Provider Implementations
- [ ] OpenAI provider: verify streaming works
- [ ] Gemini provider: verify streaming works (may need different API format)
- [ ] Ollama provider: wire up to local model server

### Phase 4 — More Playbooks
- [ ] "Interview" playbook (focus on technical Q&A)
- [ ] "Sales" playbook (focus on objection handling, talking points)
- [ ] "Exam" playbook (study mode — generates questions from visible content)

## Troubleshooting

**Backend can't connect to LLM:**
- Check `.env` has a valid `ANTHROPIC_API_KEY` (or whichever provider you configured)
- Verify `WISP_PROVIDER=anthropic` matches your API key

**Overlay doesn't appear:**
- Check Electron is actually launching: look for app icon in taskbar
- On Linux: X11 vs Wayland — Wayland may have additional requirements

**Electron dev server not hot-reloading:**
- Kill the process and restart `npm run dev`
- Make sure `npm run dev:vite` is also running (it should be via concurrently)

**Whisper transcription not working:**
- The standalone script exists at `backend/transcription/whisper_stream.py` — run it manually first to verify it works
- Check your system mic is accessible: `python -c "import sounddevice; print(sounddevice.query_devices())"`
