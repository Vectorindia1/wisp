import { app, BrowserWindow, globalShortcut, desktopCapturer, ipcMain, screen, Tray, Menu, nativeImage } from 'electron';
import { ChildProcess, spawn } from 'child_process';
import path from 'path';
import http from 'http';
import { loadSettings, saveSettings, settingsToEnv, hasApiKeyConfigured, WispSettings } from './settings';

let overlayWindow: BrowserWindow | null = null;
let settingsWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let backendProcess: ChildProcess | null = null;
let currentSettings: WispSettings = loadSettings();

const BACKEND_URL = process.env.WISP_BACKEND_URL || 'http://127.0.0.1:8137';

// In dev, a developer runs `python backend/main.py` themselves (see SETUP.md)
// so main.ts leaves the backend alone. In a packaged build there is no
// Python on the end user's machine -- we spawn the PyInstaller-bundled
// executable that electron-builder placed under process.resourcesPath (see
// package.json's `extraResources` and scripts/build-backend.*).
function backendExecutablePath(): string {
  const exeName = process.platform === 'win32' ? 'wisp-backend.exe' : 'wisp-backend';
  return path.join(process.resourcesPath, 'backend', exeName);
}

function startBundledBackend() {
  if (!app.isPackaged) return; // dev mode: developer runs the backend manually

  const exePath = backendExecutablePath();
  backendProcess = spawn(exePath, [], {
    cwd: path.dirname(exePath),
    windowsHide: true,
    // BYOK: the provider/playbook/API keys the user set in the Settings
    // window get here as env vars -- see settings.ts's settingsToEnv().
    // The backend's own env vars (PATH etc.) must still flow through, so
    // this is layered over process.env, not a replacement for it.
    env: { ...process.env, ...settingsToEnv(currentSettings) },
  });

  backendProcess.stdout?.on('data', (d) => console.log(`[backend] ${d}`));
  backendProcess.stderr?.on('data', (d) => console.error(`[backend] ${d}`));
  backendProcess.on('exit', (code) => {
    console.error(`[backend] exited with code ${code}`);
    backendProcess = null;
  });
}

function stopBundledBackend() {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill();
    backendProcess = null;
  }
}

// Called after Settings are saved so a new/changed API key or provider
// takes effect immediately instead of requiring the user to know to
// relaunch the app. No-op in dev mode, same reasoning as startBundledBackend.
async function restartBundledBackend() {
  if (!app.isPackaged) return;
  stopBundledBackend();
  startBundledBackend();
  await waitForBackend().catch((e) => console.error('[wisp] Backend restart did not come up healthy:', e));
}

// The bundled backend takes a moment to bind its port after spawn. Poll
// /health instead of guessing a fixed delay before loading the overlay UI,
// which fetches on first capture and would otherwise race a cold start.
function waitForBackend(timeoutMs = 15000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      http
        .get(`${BACKEND_URL}/health`, (res) => {
          res.resume();
          if (res.statusCode === 200) resolve();
          else retry();
        })
        .on('error', retry);
    };
    const retry = () => {
      if (Date.now() > deadline) return reject(new Error('Backend did not become healthy in time'));
      setTimeout(attempt, 300);
    };
    attempt();
  });
}

function createOverlayWindow() {
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  overlayWindow = new BrowserWindow({
    width: 420,
    height: 560,
    x: width - 440,
    y: 40,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    resizable: true,
    skipTaskbar: true,
    focusable: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Keep overlay above fullscreen apps / spaces on macOS
  overlayWindow.setAlwaysOnTop(true, 'screen-saver');
  overlayWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });

  // --- Core feature: exclude this window from screen capture / recording. ---
  // On macOS: sets NSWindow.sharingType = .none under the hood.
  // On Windows 10 2004+: sets SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE).
  // On Linux: currently a NO-OP. There is no X11 equivalent; Wayland support is
  // compositor-dependent and unimplemented here. See docs/PRD.md §6.3.
  overlayWindow.setContentProtection(true);

  const devServerUrl = process.env.VITE_DEV_SERVER_URL;
  if (devServerUrl) {
    overlayWindow.loadURL(devServerUrl);
  } else {
    overlayWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
  }
}

// Separate, normal (non-transparent, framed) window for Settings -- deliberately
// distinct from the overlay so it's unambiguous this is a "real" app window,
// not part of the invisible-to-screen-share surface. It does NOT get
// setContentProtection: you want to see your own settings when configuring them.
function openSettingsWindow() {
  if (settingsWindow) {
    settingsWindow.show();
    settingsWindow.focus();
    return;
  }

  settingsWindow = new BrowserWindow({
    width: 480,
    height: 640,
    title: 'Wisp Settings',
    resizable: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  const devServerUrl = process.env.VITE_DEV_SERVER_URL;
  if (devServerUrl) {
    settingsWindow.loadURL(`${devServerUrl}/settings.html`);
  } else {
    settingsWindow.loadFile(path.join(__dirname, '../renderer/settings.html'));
  }

  settingsWindow.on('closed', () => {
    settingsWindow = null;
  });
}

function createTray() {
  const iconPath = path.join(process.resourcesPath, 'build', 'icon.png');
  let icon = nativeImage.createFromPath(iconPath);
  if (icon.isEmpty()) {
    // Dev mode: resourcesPath doesn't contain our build/ dir the way a
    // packaged app's does. Fall back to the repo-relative copy instead of
    // showing a blank tray icon during `npm run dev`.
    icon = nativeImage.createFromPath(path.join(__dirname, '../../build/icon.png'));
  }
  tray = new Tray(icon.resize({ width: 22, height: 22 }));
  tray.setToolTip('Wisp');
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Settings…', click: () => openSettingsWindow() },
      { label: 'Toggle overlay (Ctrl+\\)', click: () => toggleOverlay() },
      { type: 'separator' },
      { label: 'Quit Wisp', click: () => app.quit() },
    ])
  );
}

function toggleOverlay() {
  if (!overlayWindow) return;
  overlayWindow.isVisible() ? overlayWindow.hide() : overlayWindow.show();
}

function registerHotkeys() {
  globalShortcut.register('CommandOrControl+\\', toggleOverlay);

  // Trigger capture: screenshot + rolling transcript -> backend -> streamed response
  globalShortcut.register('CommandOrControl+Enter', async () => {
    await triggerCapture();
  });
}

async function triggerCapture() {
  if (!overlayWindow) return;

  const sources = await desktopCapturer.getSources({
    types: ['screen'],
    thumbnailSize: { width: 1920, height: 1080 },
  });

  const primary = sources[0];
  if (!primary) return;

  const imageDataUrl = primary.thumbnail.toDataURL();

  // Hand off to the renderer, which owns the fetch/streaming connection to the
  // Python backend (keeps main process thin; renderer already has the UI state
  // for showing the streamed response).
  overlayWindow.webContents.send('capture:triggered', {
    image: imageDataUrl,
    backendUrl: BACKEND_URL,
  });
}

ipcMain.handle('backend:url', () => BACKEND_URL);
ipcMain.handle('settings:get', () => currentSettings);
ipcMain.handle('settings:save', async (_event, next: WispSettings) => {
  currentSettings = next;
  saveSettings(next);
  await restartBundledBackend();
  return { ok: true };
});
ipcMain.handle('settings:open', () => openSettingsWindow());

app.whenReady().then(async () => {
  startBundledBackend();

  if (app.isPackaged) {
    try {
      await waitForBackend();
    } catch (e) {
      console.error('[wisp] Backend failed to start:', e);
    }
  }

  createOverlayWindow();
  createTray();
  registerHotkeys();

  // First run (or any run with no key set for the chosen provider): open
  // Settings automatically instead of leaving the user staring at an
  // overlay that will silently fail on every capture. Ollama needs no key,
  // so it's exempt (see hasApiKeyConfigured).
  if (!hasApiKeyConfigured(currentSettings)) {
    openSettingsWindow();
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createOverlayWindow();
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
  stopBundledBackend();
});

app.on('window-all-closed', () => {
  // Overlay has skipTaskbar + no explicit close button, and Settings closing
  // shouldn't quit the whole app (the tray icon is the app's real "still
  // running" indicator) -- only quit here on non-macOS if literally every
  // window (including a hidden-but-destroyed overlay) is gone, mirroring
  // the original behavior.
  if (process.platform !== 'darwin') app.quit();
});
