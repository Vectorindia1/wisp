#!/usr/bin/env node
/**
 * Bundles the Python backend into a standalone executable via PyInstaller,
 * so the packaged Electron app doesn't require the end user to have Python
 * installed (see src/main/main.ts's startBundledBackend()).
 *
 * IMPORTANT: PyInstaller does not cross-compile. Running this on Linux
 * produces a Linux binary, on macOS a macOS binary, on Windows a
 * wisp-backend.exe. To ship all three platforms you must run
 * `npm run build:win` / `build:mac` / `build:linux` on that actual OS (or in
 * CI on that OS's runner -- see .github/workflows/build.yml).
 *
 * Output: dist-backend/wisp-backend/ (matches package.json's
 * build.extraResources "from" path, which electron-builder then copies into
 * the packaged app's resources/backend/ directory).
 */
const { execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const root = path.resolve(__dirname, '..');
const isWindows = process.platform === 'win32';

function venvPython() {
  const venvDir = path.join(root, 'venv');
  const candidates = isWindows
    ? [path.join(venvDir, 'Scripts', 'python.exe')]
    : [path.join(venvDir, 'bin', 'python3'), path.join(venvDir, 'bin', 'python')];
  const found = candidates.find((p) => fs.existsSync(p));
  if (found) return found;
  console.warn(
    '[build-backend] No venv found at ./venv -- falling back to system Python. ' +
      'Run `python3 -m venv venv && source venv/bin/activate && pip install -r backend/requirements.txt pyinstaller` first for a clean build.'
  );
  return isWindows ? 'python' : 'python3';
}

function run(cmd, args) {
  console.log(`[build-backend] $ ${cmd} ${args.join(' ')}`);
  execFileSync(cmd, args, { stdio: 'inherit', cwd: root });
}

const python = venvPython();

// Fail fast with a clear message rather than a wall of PyInstaller import
// errors if requirements were never installed into this venv.
try {
  execFileSync(python, ['-c', 'import fastapi, uvicorn, anthropic, PyInstaller'], { cwd: root });
} catch {
  console.error(
    '[build-backend] Missing dependencies. Run:\n' +
      '  python3 -m venv venv\n' +
      (isWindows ? '  venv\\Scripts\\activate\n' : '  source venv/bin/activate\n') +
      '  pip install -r backend/requirements.txt pyinstaller\n'
  );
  process.exit(1);
}

// Clean previous build output so stale files never ship silently.
for (const dir of ['dist-backend', 'build-backend']) {
  fs.rmSync(path.join(root, dir), { recursive: true, force: true });
}

run(python, [
  '-m',
  'PyInstaller',
  'backend/wisp_backend.spec',
  '--distpath',
  'dist-backend',
  '--workpath',
  'build-backend',
  '--noconfirm',
]);

const exeName = isWindows ? 'wisp-backend.exe' : 'wisp-backend';
const exePath = path.join(root, 'dist-backend', 'wisp-backend', exeName);
if (!fs.existsSync(exePath)) {
  console.error(`[build-backend] Expected output not found at ${exePath}`);
  process.exit(1);
}
console.log(`[build-backend] Done: ${exePath}`);
