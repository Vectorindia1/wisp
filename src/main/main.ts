import { app, BrowserWindow, globalShortcut, desktopCapturer, ipcMain, screen } from 'electron';
import { ChildProcess, spawn } from 'child_process';
import path from 'path';
import http from 'http';

let overlayWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;

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

function registerHotkeys() {
  // Toggle overlay visibility
  globalShortcut.register('CommandOrControl+\\', () => {
    if (!overlayWindow) return;
    overlayWindow.isVisible() ? overlayWindow.hide() : overlayWindow.show();
  });

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

app.whenReady().then(async () => {
  startBundledBackend();

  if (app.isPackaged) {
    try {
      await waitForBackend();
    } catch (e) {
      // Surfacing this as a dialog (rather than a silently blank overlay) is
      // a settings-UI-milestone TODO -- for now it's at least visible in the
      // packaged app's log file instead of failing every capture forever.
      console.error('[wisp] Backend failed to start:', e);
    }
  }

  createOverlayWindow();
  registerHotkeys();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createOverlayWindow();
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
  stopBundledBackend();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
