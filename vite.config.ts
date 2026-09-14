import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  root: 'src/renderer',
  base: './',
  build: {
    outDir: '../../dist/renderer',
    emptyOutDir: true,
    rollupOptions: {
      // Two windows, two HTML entries: the overlay (index.html) and the
      // Settings window (settings.html, see main.ts's openSettingsWindow).
      input: {
        index: resolve(__dirname, 'src/renderer/index.html'),
        settings: resolve(__dirname, 'src/renderer/settings.html'),
      },
    },
  },
  server: {
    port: 5173,
  },
});
