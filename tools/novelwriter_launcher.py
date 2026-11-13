#!/usr/bin/env python3
"""Launch novelWriter with on-demand USB synchronization of registered projects."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

CONFIG = Path.home() / ".config" / "novelmobile" / "projects.json"
SYNC = Path(__file__).resolve().parent / "sync_usb.py"
ADB_DIR = Path.home() / "Android" / "Sdk" / "platform-tools"
if ADB_DIR.is_dir():
    os.environ["PATH"] = str(ADB_DIR) + os.pathsep + os.environ.get("PATH", "")


def load_projects():
    if not CONFIG.exists():
        return []
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    if not isinstance(data, list) or not all(isinstance(x, str) for x in data):
        raise ValueError(f"Invalid project registry: {CONFIG}")
    return data


def save_projects(projects):
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    tmp = CONFIG.with_suffix(".tmp")
    tmp.write_text(json.dumps(projects, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, CONFIG)


def device_connected():
    adb = shutil.which("adb")
    if not adb:
        print("ADB unavailable; USB sync skipped.", file=sys.stderr)
        return False
    result = subprocess.run([adb, "devices"], capture_output=True, text=True)
    if result.returncode:
        print("ADB check failed; USB sync skipped.", file=sys.stderr)
        return False
    devices = [line for line in result.stdout.splitlines()[1:] if line.endswith("\tdevice")]
    if len(devices) != 1:
        if any("\tunauthorized" in line or "\toffline" in line for line in result.stdout.splitlines()):
            raise RuntimeError("Pixel is connected but not authorized or offline. Unlock and authorize USB debugging.")
        print("No authorized Pixel connected; USB sync skipped.", file=sys.stderr)
        return False
    return True


def sync_projects(projects, phase):
    if not projects or not device_connected():
        return True
    print(f"Novel Mobile: {phase} synchronization for {len(projects)} project(s).", flush=True)
    all_ok = True
    for path in projects:
        print(f"Syncing {path}", flush=True)
        result = subprocess.run([sys.executable, str(SYNC), path], check=False)
        if result.returncode:
            print(f"SYNC FAILED for {path} (exit {result.returncode}).", file=sys.stderr)
            all_ok = False
    return all_ok


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action")
    add = sub.add_parser("add", help="Register an existing novelWriter project")
    add.add_argument("project", type=Path)
    remove = sub.add_parser("remove", help="Unregister a project")
    remove.add_argument("project", type=Path)
    sub.add_parser("list", help="List registered projects")
    run = sub.add_parser("run", help="Sync, launch novelWriter, sync on exit")
    run.add_argument("novelwriter_args", nargs="*")
    args = parser.parse_args()
    action = args.action or "run"
    projects = load_projects()
    if action == "add":
        project = args.project.expanduser().resolve()
        if not (project / "nwProject.nwx").is_file() and not (project / "project.json").is_file():
            parser.error("Expected project directory containing nwProject.nwx or project.json")
        if str(project) not in projects:
            projects.append(str(project))
            save_projects(projects)
        print(f"Registered {project}")
        print("Existing phone copies require an initialized baseline before automated syncing.")
        return 0
    if action == "remove":
        project = str(args.project.expanduser().resolve())
        save_projects([p for p in projects if p != project])
        print(f"Unregistered {project}")
        return 0
    if action == "list":
        for project in projects:
            print(project)
        return 0
    if action != "run":
        parser.error("Unknown action")
    try:
        pre_sync_ok = sync_projects(projects, "before launch")
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    if not pre_sync_ok:
        print("Sync failed. novelWriter NOT launched to avoid editing an unsynchronized project.", file=sys.stderr)
        return 2
    binary = shutil.which("novelwriter")
    if binary is None:
        print("novelwriter executable not found on PATH.", file=sys.stderr)
        return 127
    print("Launching novelWriter...", flush=True)
    try:
        exit_code = subprocess.run([binary, *(args.novelwriter_args if args.action == "run" else [])]).returncode
    except KeyboardInterrupt:
        exit_code = 130
    try:
        post_sync_ok = sync_projects(projects, "after exit")
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 3
    if not post_sync_ok:
        print("Post-exit sync failed; changes remain on their respective devices.", file=sys.stderr)
        return 3
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
