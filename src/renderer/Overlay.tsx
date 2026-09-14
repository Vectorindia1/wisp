import React, { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    wisp: {
      getBackendUrl: () => Promise<string>;
      onCaptureTriggered: (cb: (payload: { image: string; backendUrl: string }) => void) => void;
      openSettings: () => Promise<void>;
    };
  }
}

export function Overlay() {
  const [response, setResponse] = useState('');
  const [status, setStatus] = useState<'idle' | 'thinking' | 'streaming' | 'error'>('idle');
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    window.wisp.onCaptureTriggered(async ({ image, backendUrl }) => {
      setResponse('');
      setStatus('thinking');

      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        const res = await fetch(`${backendUrl}/capture`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ image }),
          signal: controller.signal,
        });

        if (!res.body) throw new Error('No response body from backend');

        setStatus('streaming');
        const reader = res.body.getReader();
        const decoder = new TextDecoder();

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          setResponse((prev) => prev + decoder.decode(value, { stream: true }));
        }
        setStatus('idle');
      } catch (err) {
        console.error(err);
        setStatus('error');
      }
    });
  }, []);

  return (
    <div
      style={{
        WebkitAppRegion: 'drag',
        width: '100%',
        height: '100%',
        borderRadius: 12,
        background: 'rgba(20, 20, 22, 0.78)',
        backdropFilter: 'blur(14px)',
        color: '#f2f2f2',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontSize: 14,
        padding: 16,
        boxSizing: 'border-box',
        overflowY: 'auto',
      } as React.CSSProperties}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          opacity: 0.6,
          fontSize: 11,
          marginBottom: 8,
        }}
      >
        <span style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}>
          wisp — {status} — ⌘⏎ to capture, ⌘\ to hide
        </span>
        <button
          onClick={() => window.wisp.openSettings()}
          title="Settings"
          style={
            {
              WebkitAppRegion: 'no-drag',
              background: 'transparent',
              border: 'none',
              color: 'inherit',
              opacity: 0.8,
              cursor: 'pointer',
              fontSize: 13,
              padding: 2,
            } as React.CSSProperties
          }
        >
          ⚙
        </button>
      </div>
      <div style={{ whiteSpace: 'pre-wrap', WebkitAppRegion: 'no-drag' } as React.CSSProperties}>
        {response || 'Waiting for capture...'}
      </div>
    </div>
  );
}
