import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('wisp', {
  getBackendUrl: () => ipcRenderer.invoke('backend:url'),
  onCaptureTriggered: (callback: (payload: { image: string; backendUrl: string }) => void) => {
    ipcRenderer.on('capture:triggered', (_event, payload) => callback(payload));
  },
  getSettings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (settings: unknown) => ipcRenderer.invoke('settings:save', settings),
  openSettings: () => ipcRenderer.invoke('settings:open'),
});
