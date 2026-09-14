import React, { useEffect, useState } from 'react';

interface WispSettings {
  provider: 'anthropic' | 'openai' | 'gemini' | 'openrouter' | 'ollama';
  playbook: string;
  apiKeys: { anthropic: string; openai: string; gemini: string; openrouter: string };
  ollamaBaseUrl: string;
}

declare global {
  interface Window {
    wisp: {
      getSettings: () => Promise<WispSettings>;
      saveSettings: (settings: WispSettings) => Promise<{ ok: boolean }>;
    };
  }
}

const PROVIDERS: { id: WispSettings['provider']; label: string; needsKey: boolean; hint: string }[] = [
  { id: 'anthropic', label: 'Anthropic (Claude)', needsKey: true, hint: 'console.anthropic.com/settings/keys' },
  { id: 'openai', label: 'OpenAI (GPT-4o)', needsKey: true, hint: 'platform.openai.com/api-keys' },
  { id: 'gemini', label: 'Google (Gemini)', needsKey: true, hint: 'aistudio.google.com/apikey' },
  {
    id: 'openrouter',
    label: 'OpenRouter (many models, one key)',
    needsKey: true,
    hint: 'openrouter.ai/keys -- default model: openai/gpt-4o-mini',
  },
  { id: 'ollama', label: 'Ollama (local, private)', needsKey: false, hint: 'No key needed -- runs on your machine' },
];

const styles = {
  page: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    background: '#18181b',
    color: '#f2f2f2',
    height: '100%',
    boxSizing: 'border-box' as const,
    padding: 24,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 16,
  },
  title: { fontSize: 18, fontWeight: 600, margin: 0 },
  subtitle: { fontSize: 12.5, opacity: 0.6, margin: 0, lineHeight: 1.5 },
  section: { display: 'flex', flexDirection: 'column' as const, gap: 8 },
  label: { fontSize: 12, opacity: 0.75, fontWeight: 600, textTransform: 'uppercase' as const, letterSpacing: 0.4 },
  providerRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '10px 12px',
    borderRadius: 8,
    cursor: 'pointer',
    border: '1px solid transparent',
  },
  input: {
    background: '#0f0f11',
    border: '1px solid #333',
    borderRadius: 6,
    color: '#f2f2f2',
    padding: '9px 10px',
    fontSize: 13,
    fontFamily: 'inherit',
    outline: 'none',
  },
  keyHint: { fontSize: 11, opacity: 0.45, marginTop: -4 },
  button: {
    background: '#6d5bd0',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    padding: '10px 18px',
    fontSize: 13.5,
    fontWeight: 600,
    cursor: 'pointer',
  },
  status: { fontSize: 12.5, opacity: 0.75 },
};

export function Settings() {
  const [settings, setSettings] = useState<WispSettings | null>(null);
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [showKey, setShowKey] = useState(false);

  useEffect(() => {
    window.wisp.getSettings().then(setSettings);
  }, []);

  if (!settings) {
    return <div style={styles.page}>Loading…</div>;
  }

  const activeProvider = PROVIDERS.find((p) => p.id === settings.provider)!;

  async function save() {
    setStatus('saving');
    await window.wisp.saveSettings(settings!);
    setStatus('saved');
    setTimeout(() => setStatus('idle'), 1800);
  }

  function setKey(provider: keyof WispSettings['apiKeys'], value: string) {
    setSettings((s) => (s ? { ...s, apiKeys: { ...s.apiKeys, [provider]: value } } : s));
  }

  return (
    <div style={styles.page}>
      <div>
        <h1 style={styles.title}>Wisp Settings</h1>
        <p style={styles.subtitle}>
          Bring your own API key -- Wisp never bundles or proxies one for you. Your key is stored
          locally on this machine and sent only to the provider you choose, directly.
        </p>
      </div>

      <div style={styles.section}>
        <span style={styles.label}>LLM Provider</span>
        {PROVIDERS.map((p) => (
          <label
            key={p.id}
            style={{
              ...styles.providerRow,
              background: settings.provider === p.id ? 'rgba(109,91,208,0.18)' : 'transparent',
              borderColor: settings.provider === p.id ? '#6d5bd0' : '#2a2a2e',
            }}
          >
            <input
              type="radio"
              name="provider"
              checked={settings.provider === p.id}
              onChange={() => setSettings({ ...settings, provider: p.id })}
            />
            <div>
              <div style={{ fontSize: 13.5 }}>{p.label}</div>
              <div style={{ fontSize: 11, opacity: 0.5 }}>{p.hint}</div>
            </div>
          </label>
        ))}
      </div>

      {activeProvider.needsKey && (
        <div style={styles.section}>
          <span style={styles.label}>{activeProvider.label} API Key</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              style={{ ...styles.input, flex: 1 }}
              type={showKey ? 'text' : 'password'}
              placeholder="Paste your API key"
              value={settings.apiKeys[settings.provider as 'anthropic' | 'openai' | 'gemini' | 'openrouter'] || ''}
              onChange={(e) =>
                setKey(settings.provider as 'anthropic' | 'openai' | 'gemini' | 'openrouter', e.target.value)
              }
              autoComplete="off"
              spellCheck={false}
            />
            <button
              type="button"
              onClick={() => setShowKey((v) => !v)}
              style={{ ...styles.button, background: '#2a2a2e', padding: '9px 12px' }}
            >
              {showKey ? 'Hide' : 'Show'}
            </button>
          </div>
          <span style={styles.keyHint}>Get one at {activeProvider.hint}</span>
        </div>
      )}

      {settings.provider === 'ollama' && (
        <div style={styles.section}>
          <span style={styles.label}>Ollama Server URL</span>
          <input
            style={styles.input}
            value={settings.ollamaBaseUrl}
            onChange={(e) => setSettings({ ...settings, ollamaBaseUrl: e.target.value })}
          />
          <span style={styles.keyHint}>Requires `ollama serve` running with a vision model, e.g. `ollama pull llava`</span>
        </div>
      )}

      <div style={{ flex: 1 }} />

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <button style={styles.button} onClick={save} disabled={status === 'saving'}>
          {status === 'saving' ? 'Saving…' : 'Save'}
        </button>
        {status === 'saved' && <span style={styles.status}>✓ Saved — backend restarted with new settings</span>}
      </div>
    </div>
  );
}
