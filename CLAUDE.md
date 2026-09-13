# CLAUDE.md

Instructions for Claude Code (or any Claude instance) working in this repository. Read this first, every session.

## Project

Open-source, cross-platform real-time AI overlay assistant (Cluely-style meeting/interview assistant). Full spec: `/docs/PRD.md`. Read it before starting any non-trivial task.

## Read on every session start

1. `/docs/PRD.md` — what we're building and why
2. `/docs/progress.md` — what's done, what's in flight, what's next
3. `/docs/memory.md` — durable decisions, gotchas, things that took multiple tries to get right
4. `/docs/architecture-decisions.md` — why we chose what we chose (don't relitigate these without a new ADR entry)

## Stack

- Frontend/shell: Electron + React + TypeScript
- Backend: Python + FastAPI
- LLM: multi-provider (Anthropic/OpenAI/Gemini/Ollama) via `/backend/providers/`
- STT: `faster-whisper` (local) as default, cloud STT as optional provider
- Packaging: `electron-builder`, GitHub Actions CI matrix

## Ground rules

- **Platform parity is not assumed.** macOS and Windows get true screen-capture exclusion (`setContentProtection`). Linux does NOT reliably get this — see PRD §6.3. Never claim Linux invisibility is solved without a tested, cited mechanism (compositor + version).
- **No telemetry without explicit opt-in.** Don't add analytics/crash reporting silently.
- **No data leaves the machine** unless a cloud provider is explicitly configured by the user. Local-first is the default posture.
- **Every provider integration goes through the abstraction in `/backend/providers/`** — no one-off API calls scattered in route handlers.
- **Update `/docs/progress.md` at the end of every work session** — even a rough bullet list of what changed and what's broken. This is how continuity survives across sessions/contributors.
- **Log non-obvious decisions to `/docs/memory.md`** — e.g. "tried X, broke Y because Z, switched to W." Future-you (or future-Claude) needs this more than clean code needs to look clean.
- Prefer small, testable PRs over big-bang changes, especially for the OS-native window/capture code — that's the part most likely to silently break on an OS update.

## Current known risks (keep in view)

- Electron's `setContentProtection` behavior on Windows has changed across Electron versions — pin and test against the specific version in use, don't assume latest behaves the same.
- System-audio loopback capture is the most platform-fragile feature (BlackHole install step on macOS, WASAPI quirks on Windows, PulseAudio/PipeWire differences on Linux). Budget extra time here.
- Wayland screen capture is a moving target across compositors (GNOME vs KDE) — don't hardcode assumptions from one distro's behavior.

## Commands

(fill in once scaffolded, e.g. `npm run dev`, `uvicorn backend.main:app --reload`, `npm run build:all`)
