# PyInstaller spec for the wisp Python backend.
#
# Builds a single-folder distribution (faster startup than --onefile, which
# unpacks to a temp dir on every launch -- matters here since Electron spawns
# this fresh every time the app opens). Must be run ON the target OS --
# PyInstaller does not cross-compile. See scripts/build-backend.* for the
# per-OS wrapper this is invoked from.
#
# Usage: pyinstaller backend/wisp_backend.spec --distpath dist-backend

import sys
from pathlib import Path

block_cipher = None

backend_dir = Path.cwd() / "backend"

a = Analysis(
    [str(backend_dir / "main.py")],
    pathex=[str(backend_dir)],
    binaries=[],
    datas=[
        (str(Path.cwd() / "playbooks"), "playbooks"),
    ],
    hiddenimports=[
        "uvicorn.logging",
        "uvicorn.loops",
        "uvicorn.loops.auto",
        "uvicorn.protocols",
        "uvicorn.protocols.http",
        "uvicorn.protocols.http.auto",
        "uvicorn.protocols.websockets",
        "uvicorn.protocols.websockets.auto",
        "uvicorn.lifespan",
        "uvicorn.lifespan.on",
        "anthropic",
        "openai",
        "google.generativeai",
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="wisp-backend",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,  # keep a console on Windows for now -- easier to debug a
                   # missing-API-key/crash than a silently-dead child process.
                   # Flip to False once the settings UI can surface backend
                   # errors in-app instead.
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="wisp-backend",
)
