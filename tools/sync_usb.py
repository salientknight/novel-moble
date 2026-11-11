#!/usr/bin/env python3
"""On-demand, conflict-safe ADB synchronization for novelWriter projects.

Requires a previously copied Android project folder under /sdcard/Documents.
Only syncs project XML, content/*.md and ToC.txt. No background daemon.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import shlex
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

REMOTE_ROOT = "/sdcard/Documents/NovelMobile"
STATE_DIR = ".novelmobile-sync"
EXCLUDED = {"nwProject.lock"}

def adb(*args, check=True):
    return subprocess.run(["adb", *args], text=True, capture_output=True, check=check)

def adb_shell(*parts, check=True):
    command = " ".join(shlex.quote(str(part)) for part in parts)
    return adb("shell", command, check=check)

def hashes(root):
    result = {}
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(root).as_posix()
        if rel in ("nwProject.nwx", "ToC.txt") or (rel.startswith("content/") and rel.endswith(".md")):
            result[rel] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result

def copy_file(source, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, dest)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Local novelWriter project directory")
    parser.add_argument("--remote", default=None, help="ADB folder on phone")
    parser.add_argument("--initialize-from-phone", action="store_true", help="Import existing phone edits with backups on first sync")
    args = parser.parse_args()
    project = args.project.expanduser().resolve()
    if not (project / "nwProject.nwx").is_file():
        parser.error("Expected a project folder containing nwProject.nwx")
    remote = args.remote or "/sdcard/Documents/" + project.name
    if not remote.startswith("/sdcard/Documents/") or ".." in Path(remote).parts:
        parser.error("Remote folder must be under /sdcard/Documents and contain no '..'")
    devices = adb("devices").stdout.splitlines()[1:]
    if len([d for d in devices if d.strip().endswith("\tdevice")]) != 1:
        sys.exit("Connect and authorize exactly one Android device first.")
    state_path = project / STATE_DIR / "baseline.json"
    state_path.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="novelmobile-") as temp:
        phone = Path(temp) / "phone"
        phone.mkdir()
        exists = adb_shell("test", "-f", remote + "/nwProject.nwx", check=False).returncode == 0
        if not exists:
            if state_path.exists():
                sys.exit("Phone project disappeared. Refusing to overwrite; restore it first.")
            adb_shell("mkdir", "-p", remote)
            result = adb("push", str(project / "nwProject.nwx"), remote + "/nwProject.nwx")
            for rel in hashes(project):
                if rel == "nwProject.nwx":
                    continue
                adb_shell("mkdir", "-p", remote + "/" + str(Path(rel).parent))
                adb("push", str(project / rel), remote + "/" + rel)
            state_path.write_text(json.dumps(hashes(project), indent=2) + "\n")
            print("Initial project copy sent to phone. Sync baseline saved.")
            return
        result = adb("pull", remote + "/.", str(phone))
        local_hash = hashes(project)
        phone_hash = hashes(phone)
        if not state_path.exists():
            if not args.initialize_from_phone:
                sys.exit("No baseline. Use --initialize-from-phone once after confirming the phone version is desired.")
            if set(local_hash) != set(phone_hash):
                sys.exit("Project file lists differ. Refusing initial import.")
            changed = [rel for rel in sorted(local_hash) if local_hash[rel] != phone_hash[rel]]
            backup = project / STATE_DIR / "backups" / ("initial-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
            for rel in changed:
                copy_file(project / rel, backup / "laptop" / rel)
                copy_file(phone / rel, backup / "phone" / rel)
            for rel in changed:
                copy_file(phone / rel, project / rel)
            state_path.write_text(json.dumps(hashes(project), indent=2) + "\n")
            print(f"Initialized sync: imported {len(changed)} phone changes, backed up both versions.")
            return
        baseline = json.loads(state_path.read_text())
        all_paths = set(local_hash) | set(phone_hash) | set(baseline)
        conflicts = []
        to_phone = []
        to_laptop = []
        for rel in sorted(all_paths):
            old = baseline.get(rel)
            l = local_hash.get(rel)
            p = phone_hash.get(rel)
            if l == p:
                continue
            if l != old and p != old:
                conflicts.append(rel)
            elif l != old:
                to_phone.append(rel)
            else:
                to_laptop.append(rel)
        if conflicts:
            print("CONFLICT: Both devices changed these files. Nothing was synchronized:")
            print("\n".join(conflicts))
            sys.exit(2)
        backup = project / STATE_DIR / "backups" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        for rel in to_laptop:
            if (project / rel).exists():
                copy_file(project / rel, backup / "laptop" / rel)
        for rel in to_phone:
            if (phone / rel).exists():
                copy_file(phone / rel, backup / "phone" / rel)
        # Copy additions and updates; refuse deletions for now to prevent data loss.
        if any(local_hash.get(rel) is None for rel in to_phone) or any(phone_hash.get(rel) is None for rel in to_laptop):
            sys.exit("Deletion detected. Refusing to sync deletions automatically.")
        for rel in to_laptop:
            copy_file(phone / rel, project / rel)
        for rel in to_phone:
            adb_shell("mkdir", "-p", remote + "/" + str(Path(rel).parent))
            adb("push", str(project / rel), remote + "/" + rel)
        state_path.write_text(json.dumps(hashes(project), indent=2) + "\n")
        print(f"Sync complete: {len(to_laptop)} phone -> laptop, {len(to_phone)} laptop -> phone.")
        if to_laptop or to_phone:
            print("Laptop backups, where needed:", backup)

if __name__ == "__main__":
    main()
