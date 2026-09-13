# Progress Log

Running log of build status. Newest entry on top. One entry per work session — keep it honest, including what's broken.

Format per entry:
```
## YYYY-MM-DD
**Done:**
-
**In progress:**
-
**Broken / blocked:**
-
**Next up:**
-
```

---

## 2026-09-13 (session 4 — Claude Code): packaged as a standalone executable
**Done:**
- **Backend now bundles into a standalone executable via PyInstaller** (`backend/wisp_backend.spec` + `scripts/build-backend.js`) — end users no longer need Python installed at all
- `main.ts` updated: in a packaged build, spawns `resources/backend/wisp-backend(.exe)` on launch, polls `/health` until ready (`waitForBackend()`), *then* shows the overlay; kills it on `will-quit`. In dev mode (`npm run dev`) this is a no-op — developer still runs the backend manually per SETUP.md, unchanged workflow
- `package.json`: added `build:backend` script + `extraResources` config so electron-builder copies the bundled backend into every packaged app
- **Verified for real, not just configured**: ran the full pipeline on this machine (`build:backend` → `tsc` → `vite build` → `electron-builder --linux dir`), then launched the actual packaged `release/linux-unpacked/wisp` binary — confirmed via `ps aux` that it auto-spawned `resources/backend/wisp-backend` with zero manual steps, and `curl /health` returned 200 while the overlay window rendered with correct geometry (same `xwininfo` check as session 3)
- Added `.github/workflows/build.yml` — CI matrix (windows-latest/macos-latest/ubuntu-latest) that builds+packages on each native OS and uploads the installer artifacts. This is the practical path to a real `.exe` without a Windows machine, since:
- **Confirmed constraint**: PyInstaller does not cross-compile. This Linux sandbox can only ever produce a Linux `wisp-backend` binary. A real `wisp-backend.exe` requires either (a) a Windows machine running `npm run build:win`, or (b) the CI workflow above. Documented in SETUP.md and the spec file's own comments.
- Updated SETUP.md with full packaging instructions (both options), `.gitignore` extended to exclude `dist-backend/`, `build-backend/`, `venv/`

**In progress:**
- Nothing — this session's scope (packaging pipeline) is complete and verified on Linux

**Broken / blocked:**
- The actual Windows `.exe` has **not been produced or run** — only the identical pipeline verified on Linux. First real Windows build should happen via the CI workflow (push a `v*` tag) or on physical Windows hardware, and needs a human to actually double-click the installer and confirm it works — this sandbox cannot do that.
- CI workflow itself is untested (no tag has been pushed yet) — first run may surface Windows/macOS-specific PyInstaller quirks (e.g. antivirus flagging an unsigned .exe, missing DLLs) not visible from Linux
- Code signing not set up for either Windows (SmartScreen will warn) or macOS (Gatekeeper will block) — see PRD §7, unchanged

**Next up (priority order):**
1. Push a `v0.1.0` tag (or run workflow_dispatch) to get the first real CI-built Windows `.exe` and macOS `.dmg`, download and smoke-test both on real hardware
2. Phase 0 (still outstanding from session 3): verify `setContentProtection` against a real Zoom/Meet screen share on the CI-built Windows/macOS binaries
3. Settings UI (still outstanding from session 3) — becomes more urgent once non-technical users are running a packaged .exe with no `.env` file to hand-edit

## Template — first real entry starts here

## 2026-09-13 (session 3 — Claude Code): first real end-to-end run
**Done:**
- **App actually built and ran for the first time** — both backend and Electron shell verified working, not just code-reviewed
- npm dependencies installed (392 packages); ran `node node_modules/electron/install.js` manually to fetch the real Electron binary (its postinstall script was blocked by `allow-scripts` policy — silent failure otherwise: `require('electron')` returns a path string instead of the API with no error, until something destructures a property off it)
- Python deps installed in a venv; `faster-whisper`/`sounddevice` excluded on this machine (its `av` dependency needs `libavformat`/`libavcodec` *-dev headers, which aren't installed here — ffmpeg the *binary* is present, but that's not sufficient for PyAV's build). Added `WISP_WHISPER_ENABLED` env flag so the backend degrades gracefully instead of crashing when those deps are missing
- **Wired Whisper into backend startup**: FastAPI `lifespan` now spawns `whisper_stream.py` as a subprocess (kept out of the async event loop since the whisper loop is blocking/CPU-bound) and tears it down on shutdown; non-fatal if it fails to start
- **Wired SQLite into main.py**: session created on startup, captures + transcript chunks now actually persisted (previously `db.py` existed but nothing called it)
- **Fixed a real bug**: provider exceptions (auth errors, rate limits, network failures) were raised *inside* the `StreamingResponse` generator after headers were already sent — client saw a dead connection with zero explanation (reproduced with curl: exit 18, truncated body). Now caught and surfaced as visible `[wisp error] ...` text in the stream, and still persisted to `captures` for debugging
- Verified full backend loop live: `/health`, `/capture` (real call to Anthropic API, real 401 with placeholder key, clean error text delivered), `/transcript` (buffer + DB), DB rows inspected directly via sqlite3
- Verified full Electron loop live: `tsc` + `vite build` both clean, app launched under Xvfb (`DISPLAY=:0.0`), confirmed via `xwininfo`/`xprop` that the overlay window exists with the *exact* coded geometry (420×560 at screen-width−440,40) and *exact* coded window state (`_NET_WM_STATE_STICKY` from `setVisibleOnAllWorkspaces`, `_NET_WM_STATE_ABOVE` from `alwaysOnTop`)
- Note for next session: sandboxed/CI environments may set `ELECTRON_RUN_AS_NODE=1` globally, which silently makes the Electron binary behave as plain Node (same undefined-API symptom as the missing-binary issue above, different cause) — `env -u ELECTRON_RUN_AS_NODE` before launching if Electron APIs come back undefined
- Wrote **SETUP.md**: build/run guide, script reference, troubleshooting section (now includes both gotchas above)

**In progress:**
- Nothing — everything attempted this session is either done or logged as blocked below

**Broken / blocked:**
- `setContentProtection`'s actual capture-exclusion behavior is **still unverified against a real screen-share** (Zoom/Meet/Teams) — Xvfb proves the window exists with the right flags, not that a real compositor honors them. This needs a human on real macOS/Windows hardware; can't be done from this sandbox.
- Whisper/audio transcription untested end-to-end on this machine (missing system libs, see above) — code path is wired but unexercised beyond "doesn't crash the backend when disabled"
- No settings UI yet — config is still `.env`-only
- Linux content-protection remains a documented no-op (unchanged from PRD)

**Next up (priority order):**
1. **Phase 0 (real verification)**: on actual macOS/Windows hardware, run `npm run dev` + backend, share screen via Zoom/Meet, confirm the overlay is genuinely absent from the shared feed
2. **Phase 0b (this machine)**: install `libavformat-dev libavcodec-dev libavdevice-dev libavutil-dev libavfilter-dev libswscale-dev libswresample-dev` (or equivalent) to unblock `faster-whisper`, then flip `WISP_WHISPER_ENABLED=true` and verify the subprocess actually transcribes and feeds `/transcript`
3. **Phase 2 (settings UI)**: React settings panel — API keys, provider switch, hotkey customization, mic device picker — currently the only way to configure anything is hand-editing `.env`
4. **Phase 3 (robustness)**: retry/restart logic if the whisper subprocess dies mid-session; structured logging instead of print statements

## 2026-09-13 (session 1)
**Done:**
- PRD drafted (`docs/PRD.md`)
- Repo scaffolding docs created (this file, CLAUDE.md, memory.md)

**In progress:**
- Nothing yet — pre-code phase

**Broken / blocked:**
- N/A

**Next up:**
- Phase 0 spike: prove `setContentProtection` works against Zoom/Meet screen share on Windows + macOS; document Linux behavior (expected: doesn't work on X11, TBD on Wayland)
- Decide project name + license (open question in PRD §10)
- Scaffold `/app` (Electron+React+TS) and `/backend` (FastAPI) skeletons
