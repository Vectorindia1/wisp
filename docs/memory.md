# Memory / Durable Notes

Things that are expensive to relearn. Not a changelog (that's `progress.md`) — this is the "don't make us debug this twice" file. Add an entry whenever something takes more than one attempt to get right, or a decision gets made that isn't obvious from the code.

Format:
```
### <short title>
**Context:** why this came up
**Decision/finding:**
**Implication for future work:**
```

---

### Screen-capture exclusion is per-OS, not a library feature
**Context:** Researching how to make the overlay invisible to Zoom/Meet/Teams screen share.
**Decision/finding:** Electron's `setContentProtection(true)` wraps native OS APIs (`NSWindow.sharingType` on macOS, `SetWindowDisplayAffinity`/`WDA_EXCLUDEFROMCAPTURE` on Windows). There is no Linux equivalent at the X11 level; Wayland support is compositor-dependent and unverified as of PRD draft.
**Implication for future work:** Never assume a single code path covers all 3 platforms for this feature. Test on real Zoom/Meet calls, not just "window looks transparent in a screenshot" — screen-share capture pipelines behave differently than local screenshots.

### System audio loopback is the most fragile cross-platform feature
**Context:** "Hear the other call participant" requires capturing system output, not just mic input.
**Decision/finding:** Needs a virtual audio device on macOS (BlackHole, requires user install step), WASAPI loopback mode on Windows, PulseAudio/PipeWire monitor source on Linux.
**Implication for future work:** Treat this as its own milestone (Phase 3 in PRD), not a subtask of basic audio capture. Expect per-OS setup instructions in docs/onboarding.

### `npm install` alone does not give you a working Electron
**Context:** First real launch attempt crashed with `Cannot read properties of undefined (reading 'handle')` on `ipcMain.handle(...)` — looked like an Electron API problem, wasn't.
**Decision/finding:** Electron's package ships a postinstall script (`node_modules/electron/install.js`) that downloads the real platform binary and writes `node_modules/electron/path.txt`. If that script is blocked (e.g. an `allow-scripts`/npm-scripts security policy), install finishes with **zero error** and `require('electron')` from within Electron's own main process just returns the module's fallback path string instead of the `{app, BrowserWindow, ipcMain, ...}` API object — so every destructured import is silently `undefined` until first used. Fix: `node node_modules/electron/install.js` manually, then verify with `node_modules/electron/dist/electron --version` (should print an Electron version like `v29.4.6`, not error).
**Implication for future work:** After any fresh `npm install` in a locked-down environment, don't trust "no errors" as proof Electron is runnable — check `node_modules/electron/path.txt` exists before debugging app code for what looks like an Electron API bug.

### `ELECTRON_RUN_AS_NODE=1` produces the identical symptom, different cause
**Context:** After fixing the missing-binary issue above, the *exact same* `ipcMain.handle` crash recurred.
**Decision/finding:** Some sandboxed/CI shells export `ELECTRON_RUN_AS_NODE=1` as a safety default. With it set, the Electron binary runs as plain Node regardless of how it's invoked — `node_modules/electron/dist/electron --version` will print a Node version (`v20.9.0`-shaped) instead of an Electron version. Same undefined-`ipcMain` crash, unrelated root cause.
**Implication for future work:** When Electron APIs come back undefined, check both: (1) does `path.txt` exist (binary installed?), (2) is `ELECTRON_RUN_AS_NODE` set in the env (`env -u ELECTRON_RUN_AS_NODE electron .` to rule it out). Don't assume the first fix was the only one needed.

### Streaming HTTP responses can't turn provider errors into HTTP error codes
**Context:** Testing `/capture` with a placeholder API key: curl reported exit 18 (transfer closed with outstanding data) and the overlay would have shown nothing — no error, no partial response, just a dead connection.
**Decision/finding:** `StreamingResponse` sends headers (status 200) before the generator body runs. By the time a provider SDK raises (`AuthenticationError`, rate limits, network errors), it's too late to change the status code — the exception just kills the stream. Fixed by wrapping the generator body in try/except and yielding a `[wisp error] ...` text chunk instead of letting the exception propagate.
**Implication for future work:** Any new provider or new streaming endpoint must follow the same pattern — catch inside the generator, never let a provider exception escape a `StreamingResponse` body uncaught. Otherwise failures are invisible to the person actually using the overlay in the moment they need it most.

### PyInstaller does not cross-compile
**Context:** Tried to produce a Windows `.exe` for the bundled backend from a Linux dev sandbox.
**Decision/finding:** PyInstaller only ever produces a binary for the OS/arch it's *running on* — there is no `--target-platform windows` equivalent. A Linux machine can only build a Linux `wisp-backend`; the real `wisp-backend.exe` must be built by a process actually running on Windows.
**Implication for future work:** `.github/workflows/build.yml`'s CI matrix (windows-latest/macos-latest/ubuntu-latest) is not a nice-to-have here, it's the only way to produce all 3 platforms' backends without owning 3 physical/VM machines. Never try to "just build it for Windows" from Linux again — go straight to CI or a real Windows box.

### `faster-whisper`'s transitive `av` (PyAV) pin needs to be overridden for CI
**Context:** First CI run failed identically on all 3 platforms at `pip install -r backend/requirements.txt` — PyAV tried to compile from source and needed `libavformat` etc. dev headers that hosted runners don't have.
**Decision/finding:** `faster-whisper==1.0.1`'s resolved `av` version had no prebuilt wheel for the runners' Python (3.11). Explicitly pinning `av>=12.3.0` (verified against PyPI's file listing to have `cp311` wheels for win_amd64/macosx/manylinux before pushing) fixed it, and it satisfies faster-whisper's own `av<13,>=11.0` constraint.
**Implication for future work:** Before bumping `faster-whisper` again, re-verify its resolved `av` version still has prebuilt wheels for whatever Python version CI uses — pin `av` explicitly rather than trusting the transitive resolution.

### electron-builder needs `publish:null` and `author.email` explicitly, even without a GitHub release
**Context:** Second CI run got much further (Windows/macOS actually built `Wisp Setup 0.1.0.exe` / the `.dmg`) but then failed with "GitHub Personal Access Token is not set". Linux failed separately and earlier with "Please specify author 'email'".
**Decision/finding:** electron-builder auto-detects a CI environment and tries to publish a GitHub release unless `"publish": null` is set in the build config — even when nothing asked it to publish. Separately, the `.deb` target specifically requires `author.email` in package.json (an empty string author isn't enough).
**Implication for future work:** Both of these look like real build failures but are pure config gaps. If a *packaging* step fails right at the end after the artifact clearly already got built (check the log for "building block map" succeeding first), suspect one of these two before anything else.

### A subprocess launched via `sys.executable` is broken inside a PyInstaller-frozen backend
**Context:** Whisper transcription auto-start used `subprocess.Popen([sys.executable, "whisper_stream.py", ...])`, mirroring how a developer would run it manually. Worked in dev; inside the packaged app the process appeared then immediately zombied.
**Decision/finding:** Inside a frozen PyInstaller executable, `sys.executable` points at the frozen binary itself (there's no separate bundled `python.exe`) — it can't be re-invoked with a script path argument the way a real Python interpreter can. Fixed by running the whisper loop on a `threading.Thread` in the same process instead of a subprocess; `whisper_stream.run()` now takes an `on_transcript` callback + a `threading.Event` to stop, and is only imported lazily inside the function that starts it.
**Implication for future work:** Never spawn `sys.executable` as if it's a generic interpreter from code that might run frozen. If a feature genuinely needs a separate OS process once packaged, it needs its own PyInstaller spec/executable, not a `sys.executable` shortcut.

### Optional native-dependency imports must stay lazy, not module-level
**Context:** While fixing the subprocess-vs-thread issue above, the whisper import got hoisted to the top of `main.py` for a moment. In a sandbox where `sounddevice` wasn't actually installed, this took down the *entire* backend at startup (`ModuleNotFoundError` before FastAPI even started) — including the core screenshot->LLM loop, which has nothing to do with audio.
**Decision/finding:** `import transcription.whisper_stream` must happen *inside* `_start_transcription_thread()`, guarded by the same try/except that already handles "audio just isn't available on this machine." A module-level import turns "optional feature unavailable" into "whole app won't start."
**Implication for future work:** Any dependency that isn't installed on every platform/environment (native audio, video, GPU libs) needs its import deferred to the function that actually uses it, wrapped in try/except — never at module scope in `main.py`.

---

## Open architecture questions not yet resolved
- Project name / trademark distance from "Cluely"
- License choice: MIT/ISC vs AGPL
- Local-first vs cloud-first default provider
