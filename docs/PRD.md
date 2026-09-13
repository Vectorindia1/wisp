# PRD: Open-Source Real-Time AI Overlay Assistant
**Codename:** (TBD — suggestions: "Glass", "Prompter", "Echo")
**Owner:** Veenu Krishna Shah
**Status:** Draft v1
**Target platforms:** Windows 10/11, macOS 12+ (Apple Silicon + Intel), Linux (X11 + Wayland)

---

## 1. Problem Statement

Cluely and similar tools (Interview Coder, etc.) demonstrate a working pattern: a desktop overlay that captures audio + screen context in real time, feeds it to an LLM, and renders suggestions in a window invisible to screen-share/recording pipelines. No open-source project currently replicates the *full* feature set cross-platform with a clean, auditable, self-hostable architecture.

## 2. Goals

- Full cross-platform parity: Windows, macOS, Linux (X11 and Wayland separately, since Wayland's screen-capture model is fundamentally different).
- Feature parity with Cluely's core loop: audio capture → transcription → screen capture → LLM reasoning → overlay rendering.
- Pluggable LLM backends: cloud (Anthropic/OpenAI/Gemini) and local (Ollama) for users who want zero data leaving their machine.
- Fully open-source, self-hostable, no telemetry by default.
- Should be usable as a **legitimate** meeting-assistant/note-taker as the default framing — the "invisible" capability is a technical feature (useful for personal teleprompting, accessibility, live-note privacy) not a marketed cheating tool. How you position and ship this publicly is your call, but the repo/README framing affects adoption, contributors, and whether platforms (Zoom, GitHub) treat it as abuse tooling.

## 3. Non-Goals (v1)

- Mobile apps (iOS/Android) — desktop only.
- Built-in CRM/ATS integrations (Cluely enterprise tier) — stub for later.
- Proctoring-bypass guarantees — we implement the standard OS-level capture-exclusion APIs; we do not chase every proctoring vendor's countermeasures.

## 4. Core User Flow

1. User launches app → transparent always-on-top overlay window appears.
2. User hits global hotkey → overlay becomes active / screenshot captured.
3. App transcribes live audio (mic + optional system-audio loopback) continuously into a rolling buffer.
4. On trigger (hotkey, voice cue, or auto-interval), app bundles: [recent transcript window] + [latest screenshot] + [user-set context/playbook] → sends to LLM.
5. LLM response streams into the overlay in real time.
6. Overlay is excluded from screen-capture/recording APIs on all 3 platforms (best-effort per OS, see §6.3).

## 5. Feature List (mapped to Cluely parity)

| Feature | Priority | Notes |
|---|---|---|
| Transparent always-on-top overlay | P0 | Core UI shell |
| Global hotkeys (toggle visibility, trigger capture) | P0 | Cross-platform hotkey lib needed |
| Screen-capture exclusion (invisible to sharing/recording) | P0 | Per-OS implementation, see §6.3 |
| Screenshot capture + vision LLM analysis | P0 | Replaces manual OCR |
| Live mic transcription | P0 | Whisper (local) or API |
| System-audio loopback capture (hear the other party) | P1 | Platform-specific virtual audio device needed |
| Streaming LLM responses in overlay | P0 | |
| Multi-provider LLM support (Anthropic/OpenAI/Gemini/Ollama) | P0 | Abstracted provider interface |
| Custom "playbooks" / system prompts per use case | P1 | Interview / sales / exam presets |
| Meeting-platform auto-detection (Zoom/Meet/Teams) | P2 | Nice-to-have, not required for core loop |
| Calendar sync | P2 | Out of scope v1 |
| Session history / transcript export | P1 | Local storage only |
| Settings UI (API keys, hotkeys, provider selection) | P0 | |
| Cross-platform installer/packaging | P0 | See §7 |

## 6. Architecture

### 6.1 High-level design

```
┌─────────────────────────────────────────┐
│              Electron Shell              │
│  (overlay window, hotkeys, screenshot,   │
│   audio capture plumbing, settings UI)   │
└───────────────┬───────────────────────────┘
                │ localhost HTTP/WebSocket
┌───────────────▼───────────────────────────┐
│         Python Backend (FastAPI)          │
│  - context buffer (transcript + screens)  │
│  - LLM provider abstraction               │
│  - Whisper transcription (local)          │
│  - playbook/prompt management             │
└───────────────┬───────────────────────────┘
                │
        ┌───────┴────────┐
        │  LLM Providers  │  Anthropic / OpenAI / Gemini / Ollama
        └────────────────┘
```

Rationale: Electron owns everything that must be OS-native (window flags, global hotkeys, screen/audio capture primitives). Python backend owns everything that's "brains" — this matches your strongest language and keeps the two concerns cleanly separated for testing.

### 6.2 Repo structure (proposed)

```
/app                    Electron frontend
  /src
    main.ts             Electron main process (window, hotkeys, capture)
    preload.ts
    /overlay             React UI for the overlay
    /settings            React UI for settings panel
/backend                Python FastAPI service
  /providers             llm provider adapters (anthropic.py, openai.py, ollama.py...)
  /transcription          whisper wrapper
  /context                rolling buffer, playbook loader
  main.py
/playbooks               yaml/json prompt presets (interview, sales, exam, general)
/docs
  PRD.md
  CLAUDE.md
  progress.md
  memory.md
  architecture-decisions.md
/scripts                 build/package scripts per OS
```

### 6.3 The hard part: screen-capture exclusion per OS

| OS | Mechanism | Notes |
|---|---|---|
| macOS | `NSWindow.sharingType = .none` | Exposed in Electron via `win.setContentProtection(true)`. Reliable, works against Zoom/Meet/Teams screen share and QuickTime recording. |
| Windows | `SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)` | Also exposed via Electron `setContentProtection(true)` on recent Electron versions (uses this Win32 API under the hood). Requires Windows 10 2004+. |
| Linux X11 | No standard OS-level equivalent | X11 has no capture-exclusion primitive; compositor-dependent hacks exist (e.g., excluding from specific window-manager capture APIs) but are unreliable. Practical approach: document this as a **known limitation** on X11, or implement a "click-through/always-minimize-on-share" heuristic instead of true exclusion. |
| Linux Wayland | Depends on compositor (e.g., GNOME/Mutter, KDE/KWin) via PipeWire's `xdg-desktop-portal` capture model | Wayland's security model is actually more amenable — some compositors support per-window capture restriction. This needs its own research spike; treat as a stretch goal, not a v1 blocker. |

**Recommendation:** Ship macOS and Windows with true invisibility as advertised. Ship Linux with the overlay working normally (visible in screen share) and document it as a platform limitation, rather than blocking the whole release chasing X11/Wayland parity.

### 6.4 Data & privacy

- No data leaves the machine unless the user configures a cloud LLM provider.
- All transcripts/screenshots stored locally (SQLite), encrypted at rest is a P1 stretch goal.
- No telemetry by default; if you add crash reporting, make it explicit opt-in.

## 7. Packaging & Distribution

- **Electron Builder** for cross-platform packaging (`.dmg`/`.exe`/`.AppImage` + `.deb`).
- Code signing: macOS requires a Developer ID cert (or Gatekeper will block it) — budget for this or document the "right-click open" workaround for unsigned builds.
- Windows: unsigned builds trigger SmartScreen warnings — same tradeoff.
- CI: GitHub Actions matrix build (windows-latest, macos-latest, ubuntu-latest) producing artifacts per tag/release.

## 8. Skills / Knowledge Needed

This is the actual skill inventory for you to build this solo (or scope out to collaborators):

**Core:**
- Electron main/renderer process architecture, IPC, `BrowserWindow` flags (`setContentProtection`, `alwaysOnTop`, `transparent`, `frame: false`)
- Node.js native modules / `desktopCapturer` API for screenshots
- React (for overlay + settings UI)
- Python + FastAPI (backend service, async patterns for streaming LLM responses over WebSocket)
- LLM API integration: streaming responses (SSE/WebSocket), multi-provider abstraction, prompt/context-window management
- `whisper.cpp` or `faster-whisper` for local STT; understanding of audio buffering/chunking for near-real-time transcription
- Cross-platform audio capture: `node-record-lpcm16` / `sox` on Linux, `BlackHole` (macOS virtual audio device) or `WASAPI loopback` (Windows) for capturing system audio (the "hear the other person" feature)
- Global hotkey registration cross-platform (`electron-globalShortcut` — verify it works under Wayland, which restricts some of this)

**Platform-specific systems knowledge:**
- macOS: `NSWindow` sharing types, Gatekeeper/notarization basics
- Windows: Win32 `SetWindowDisplayAffinity`, code-signing/SmartScreen
- Linux: X11 vs Wayland capture model differences, `xdg-desktop-portal`/PipeWire basics

**Packaging/DevOps:**
- `electron-builder` config for 3-platform builds
- GitHub Actions CI matrix builds
- Auto-update mechanism (`electron-updater`) — optional but expected of a polished app

**Product/Legal (soft skills, not code, but matters):**
- Positioning/README framing — this determines whether this reads as "open-source meeting assistant" or "cheating tool," which affects GitHub policy, App Store distribution (if ever), and contributor comfort.

## 9. Milestones

| Phase | Deliverable |
|---|---|
| 0 — Spike | Prove overlay + content-protection works on all 3 OSes (Linux caveat noted) |
| 1 — Core loop | Screenshot → vision LLM → streamed response in overlay (no audio yet) |
| 2 — Audio | Mic transcription (local Whisper) feeding into context |
| 3 — System audio | Loopback capture for hearing the other call participant |
| 4 — Multi-provider | Provider abstraction, settings UI, Ollama local mode |
| 5 — Playbooks | Prompt presets, custom context injection |
| 6 — Packaging | Cross-platform installers, CI, signed builds |
| 7 — Polish | Session history, export, hotkey customization, docs |

## 10. Open Questions

- Do you want a project name/brand now, or ship under a generic name to avoid trademark friction with "Cluely"?
- Local-first (Whisper/Ollama default) or cloud-first (faster/better quality, but costs + requires API keys) as the out-of-box default?
- License: MIT/ISC (matches the existing "free-cluely" clone) or AGPL to discourage closed-source SaaS forks?
