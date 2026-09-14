import { app } from 'electron';
import fs from 'fs';
import path from 'path';

// BYOK: users provide their own LLM API key(s) rather than one being baked
// into the app. Persisted as plain JSON in Electron's per-OS userData dir
// (e.g. %APPDATA%\wisp on Windows, ~/Library/Application Support/wisp on
// macOS, ~/.config/wisp on Linux) -- NOT in the app install directory,
// which may not be writable and would be wiped on reinstall/update.
//
// This is local-only storage, same posture as the rest of the app (see
// docs/PRD.md §6.4): the key never leaves the machine except in the
// provider's own API request. Written in plaintext, not OS-keychain-backed
// -- acceptable for a local single-user desktop tool, but worth revisiting
// (see docs/memory.md) if this ever becomes multi-user.

export interface WispSettings {
  provider: 'anthropic' | 'openai' | 'gemini' | 'openrouter' | 'ollama';
  playbook: string;
  apiKeys: {
    anthropic: string;
    openai: string;
    gemini: string;
    openrouter: string;
  };
  ollamaBaseUrl: string;
}

const DEFAULT_SETTINGS: WispSettings = {
  provider: 'anthropic',
  playbook: 'general',
  apiKeys: { anthropic: '', openai: '', gemini: '', openrouter: '' },
  ollamaBaseUrl: 'http://127.0.0.1:11434',
};

function settingsPath(): string {
  return path.join(app.getPath('userData'), 'settings.json');
}

export function loadSettings(): WispSettings {
  try {
    const raw = fs.readFileSync(settingsPath(), 'utf-8');
    const parsed = JSON.parse(raw);
    // Shallow-merge over defaults so a settings.json from an older version
    // (missing a newer field, e.g. a provider added later) doesn't crash
    // the app -- it just falls back to that field's default.
    return {
      ...DEFAULT_SETTINGS,
      ...parsed,
      apiKeys: { ...DEFAULT_SETTINGS.apiKeys, ...(parsed.apiKeys || {}) },
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: WispSettings): void {
  fs.mkdirSync(path.dirname(settingsPath()), { recursive: true });
  fs.writeFileSync(settingsPath(), JSON.stringify(settings, null, 2), 'utf-8');
}

// Translates saved settings into the env vars the Python backend already
// reads via os.getenv (see backend/main.py, backend/providers/__init__.py)
// -- no backend code changes needed, we just control its environment at
// spawn time.
export function settingsToEnv(settings: WispSettings): NodeJS.ProcessEnv {
  return {
    WISP_PROVIDER: settings.provider,
    WISP_PLAYBOOK: settings.playbook,
    ANTHROPIC_API_KEY: settings.apiKeys.anthropic,
    OPENAI_API_KEY: settings.apiKeys.openai,
    GEMINI_API_KEY: settings.apiKeys.gemini,
    OPENROUTER_API_KEY: settings.apiKeys.openrouter,
    WISP_OLLAMA_BASE_URL: settings.ollamaBaseUrl,
  };
}

export function hasApiKeyConfigured(settings: WispSettings): boolean {
  if (settings.provider === 'ollama') return true; // no key needed, just a local server
  return Boolean(settings.apiKeys[settings.provider]?.trim());
}
