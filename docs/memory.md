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

---

## Open architecture questions not yet resolved
- Project name / trademark distance from "Cluely"
- License choice: MIT/ISC vs AGPL
- Local-first vs cloud-first default provider
