#!/usr/bin/env python3
"""Install a clickable COSMIC desktop entry for the Novel Mobile sync launcher."""
from pathlib import Path
import os
import shutil

ROOT = Path(__file__).resolve().parent
LAUNCHER = ROOT / "novelwriter_launcher.py"
HOME = Path.home()
APPLICATIONS = HOME / ".local/share/applications"
TARGET = APPLICATIONS / "novelwriter-synced.desktop"
BIN = shutil.which("python3") or "/usr/bin/python3"
ADB = HOME / "Android/Sdk/platform-tools"
ICON = "accessories-text-editor"

def main():
    if not LAUNCHER.is_file():
        raise SystemExit("Cannot locate novelwriter_launcher.py")
    APPLICATIONS.mkdir(parents=True, exist_ok=True)
    entry = f"""[Desktop Entry]
Type=Application
Version=1.0
Name=novelWriter (Auto Sync)
Comment=Sync your Pixel before and after novelWriter
Exec={BIN} {LAUNCHER} run
Icon={ICON}
Terminal=true
Categories=Office;WordProcessor;
StartupNotify=true
"""
    TARGET.write_text(entry, encoding="utf-8")
    TARGET.chmod(0o644)
    print(f"Installed desktop entry: {TARGET}")
    print("Open your COSMIC application launcher and select 'novelWriter (Auto Sync)'.")
    print("The terminal remains visible to show sync errors; it closes when the launcher exits.")
    print("Do not use the original novelWriter shortcut when you need synchronization.")

if __name__ == "__main__":
    main()
