# Architecture Decision Records

Short-form ADRs. Each one: what was decided, what alternatives were considered, why this won. Don't relitigate a decision here without adding a new dated entry explaining what changed.

---

## ADR-001: Electron shell + Python backend, split by concern
**Date:** 2026-09-13
**Decision:** Electron/React owns OS-native surface (window management, hotkeys, screen/audio capture primitives). Python/FastAPI owns "brains" (LLM orchestration, transcription, context management), talking to Electron over localhost HTTP/WebSocket.
**Alternatives considered:**
- Pure Electron/Node backend (keeps everything in one process/language) — rejected because Python has stronger LLM/ML tooling (whisper bindings, provider SDKs) and matches the primary dev's strongest language.
- Pure Python desktop app (e.g. PyQt/Tkinter) — rejected because native overlay/transparency/content-protection support across 3 OSes is far more mature and documented in Electron.
**Consequence:** Two runtimes to package together; adds complexity to the build/packaging step (Electron app needs to bundle or spawn the Python backend). Revisit if packaging pain becomes severe — alternative would be rewriting the backend in Node and eating the weaker ML tooling.

## ADR-002: Linux does not get true screen-capture exclusion in v1
**Date:** 2026-09-13
**Decision:** Ship Linux with the overlay visible during screen share rather than blocking release on X11/Wayland capture-exclusion research.
**Alternatives considered:** Delay Linux release until parity found; implement fragile compositor-specific hacks.
**Consequence:** Linux users get a functionally complete app minus the "invisible" property. Document clearly in README/onboarding. Revisit as a stretch goal once core product is stable — Wayland's portal-based model may eventually support this properly.

---

## Template for new entries
```
## ADR-XXX: <short title>
**Date:**
**Decision:**
**Alternatives considered:**
**Consequence:**
```
