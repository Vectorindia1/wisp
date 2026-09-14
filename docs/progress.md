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

## 2026-09-15 (session 5 — Claude Code): BYOK settings UI + real Windows .exe shipped
**Done:**
- **First real CI-built installers exist and were downloaded/verified**: `Wisp Setup 0.1.0.exe` (110.6 MB, confirmed via `file` as a genuine PE32 NSIS installer), plus macOS `.dmg` and Linux `.AppImage`/`.deb`, all from `.github/workflows/build.yml` run 34774866718 on github.com/Vectorindia1/wisp
- Fixed 2 real CI bugs found getting there (both now in docs/memory.md): the `av`/PyAV wheel pin, and electron-builder's `publish:null`/`author.email` requirements
- **BYOK settings UI, fully built and verified live**: new `Settings.tsx` window (separate framed BrowserWindow, not part of the invisible overlay) with provider picker (Anthropic/OpenAI/Gemini/OpenRouter/Ollama), API key input per provider, Ollama base-URL field, Save button
- Settings persist to `userData/settings.json` (`src/main/settings.ts`) and get translated to env vars for the spawned backend (`settingsToEnv()`) -- **verified end-to-end via UI automation**: clicked OpenRouter radio, typed a test key, clicked Save, confirmed via `/proc/<pid>/environ` that the newly-spawned backend process actually received `WISP_PROVIDER=openrouter` and `OPENROUTER_API_KEY=...`
- First-run UX: Settings window auto-opens if no API key is configured for the selected provider, instead of leaving a silently-broken overlay
- Added system tray icon (Settings.../Toggle overlay/Quit menu) and a gear button inside the overlay itself, both opening the same Settings window
- Added **Gemini** and **OpenRouter** providers (`backend/providers/gemini_provider.py`, `openrouter_provider.py`) -- Gemini was advertised in the PRD/requirements.txt but never actually implemented until now; OpenRouter added per this session's request (reuses the `openai` SDK against OpenRouter's OpenAI-compatible endpoint, one key/many models)
- Generated a real app icon (`build/icon.png`, `scripts/generate_icon.py` -- a wisp/spiral mark on a dark gradient) instead of shipping with Electron's default icon; wired into `electron-builder`'s `icon` config and as a runtime extraResource for the tray icon
- **Found and fixed a real production bug independent of this session's feature work**: whisper transcription auto-start used `subprocess.Popen([sys.executable, ...])`, which is broken inside every PyInstaller-frozen backend (sys.executable there is the frozen binary itself, not a python.exe) -- silently produced a zombie process in every packaged build to date. Rewrote as an in-process background thread (`whisper_stream.run()` now takes an `on_transcript` callback + `threading.Event`)
- Found and fixed a second bug introduced while fixing the first: hoisting the whisper import to module level meant a missing `sounddevice`/`faster-whisper` install would crash the *entire* backend at startup, not just disable transcription. Made the import lazy, scoped inside the try/except that already handles "audio unavailable"
- All of the above verified together in one final live run: packaged app launched, backend came up (whisper gracefully disabled with a clear log line, since this dev sandbox is missing `sounddevice`), overlay + Settings windows both rendered, screenshotted via `xwd`+netpbm for visual confirmation

**In progress:**
- Nothing — rebuilding CI with all of today's fixes is the very next action after this log entry

**Broken / blocked:**
- This dev sandbox runs Python 3.14 (too new for `av` to have a prebuilt wheel), so whisper transcription could NOT be verified working end-to-end *here* -- only that it degrades gracefully when unavailable. Real verification needs the CI-built Windows/macOS installer's own logs, or a normal (3.10-3.12) local Python.
- Settings UI has no model picker yet (OpenRouter's model is env-var-only, `WISP_OPENROUTER_MODEL`) and no way to toggle `WISP_WHISPER_ENABLED` from the UI -- both `.env`-only for now
- No OS keychain integration for the API key -- `settings.json` is plaintext, acceptable for a local single-user tool per docs/memory.md but worth flagging if this ever becomes multi-user

**Next up (priority order):**
1. Push these fixes, rebuild CI, download + hand the user the updated Windows `.exe` with BYOK
2. Phase 0 (still outstanding across 3 sessions now): verify `setContentProtection` against a real Zoom/Meet screen share -- needs a human on real Windows/macOS hardware, cannot be done from any sandbox
3. Model picker in Settings for OpenRouter/Ollama, and a `WISP_WHISPER_ENABLED` toggle in the UI instead of `.env`-only
4. Mobile: user has flagged converting this to an APK for mobile as the next major milestone after the desktop app is solid -- worth a design discussion first, since the whole architecture (Electron overlay + screen-capture-exclusion APIs) is desktop-OS-specific and doesn't port directly to Android/iOS

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
