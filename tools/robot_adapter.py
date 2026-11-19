#!/usr/bin/env python3
"""US-003 adapter for the Android operator application."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = [
    {"id": "android-default", "description": "assemble, unit test, and lint the debug app", "gate": 0, "level": "host"},
    {"id": "android-device", "description": "install and run instrumentation tests on one selected ADB device", "gate": 1, "level": "device"},
    {"id": "ANDROID-RECONNECT-001", "description": "qualified reconnect without automatic motion resume", "gate": 1, "level": "device"},
]


def now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def base(command: str, level: str = "static") -> dict[str, Any]:
    stamp = now()
    return {"schema_version": 1, "command": command, "scenario": None, "scenario_version": None, "started_at": stamp, "finished_at": stamp, "outcome": "pass", "verification_level": level, "physical_stage": None, "environment": {}, "repositories": [], "devices": [], "checks": [], "artifacts": [], "approvals": [], "evidence_id": None, "safe_next_action": None}


def item(check_id: str, outcome: str, expected: str, observed: str, duration: int = 0, category: str | None = None, code: int | None = None, command: list[str] | None = None) -> dict[str, Any]:
    return {"id": check_id, "outcome": outcome, "expected_signal": expected, "observed": observed[-4000:], "duration_ms": duration, "failure_category": category, "exit_code": code, "command": command}


def finish(value: dict[str, Any]) -> dict[str, Any]:
    order = {"pass": 0, "skipped": 1, "inconclusive": 2, "blocked": 3, "fail": 4}
    value["outcome"] = max((entry["outcome"] for entry in value["checks"]), key=order.get) if value["checks"] else "inconclusive"
    value["finished_at"] = now()
    return value


def run(command: list[str], timeout: int = 1800, extra_env: dict[str, str] | None = None) -> tuple[subprocess.CompletedProcess[str], int]:
    started = time.monotonic()
    env = dict(os.environ)
    env.setdefault("GRADLE_USER_HOME", "/tmp/ai-robot-android-gradle")
    selected_java_home = compatible_java_home()
    if selected_java_home:
        env["JAVA_HOME"] = str(selected_java_home)
        env["PATH"] = str(selected_java_home / "bin") + os.pathsep + env.get("PATH", "")
    env.update(extra_env or {})
    try:
        completed = subprocess.run(command, cwd=ROOT, env=env, text=True, capture_output=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as exc:
        completed = subprocess.CompletedProcess(command, 124, exc.stdout or "", exc.stderr or "timeout")
    return completed, int((time.monotonic() - started) * 1000)


def repo() -> dict[str, Any]:
    sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False).stdout.strip()
    dirty = bool(subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, text=True, capture_output=True, check=False).stdout.strip())
    return {"name": "android", "sha": sha, "dirty": dirty}


def describe() -> dict[str, Any]:
    value = base("android adapter describe")
    value["environment"] = {"adapter_protocol": 1, "platform": "android", "scenarios": SCENARIOS}
    value["checks"].append(item("android.adapter", "pass", "adapter protocol 1", "adapter protocol 1"))
    return finish(value)


def adb_devices() -> list[dict[str, Any]]:
    adb = android_tool("adb")
    if not adb:
        return []
    completed, _ = run([adb, "devices", "-l"], 15)
    devices = []
    for line in completed.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2:
            devices.append({"kind": "adb", "stable_id": fields[0], "state": fields[1], "metadata": fields[2:]})
    return devices


def android_sdk() -> Path | None:
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured and Path(configured).is_dir():
        return Path(configured)
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidate = Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
                if candidate.is_dir():
                    return candidate
    return None


def java_major(java: Path) -> int | None:
    completed = subprocess.run([str(java), "-version"], text=True, capture_output=True, check=False)
    match = __import__("re").search(r'version "([0-9]+)', completed.stderr + completed.stdout)
    return int(match.group(1)) if match else None


def compatible_java_home() -> Path | None:
    configured = os.environ.get("JAVA_HOME")
    candidates = [Path(configured)] if configured else []
    current = shutil.which("java")
    if current:
        candidates.append(Path(current).resolve().parent.parent)
    candidates.extend(sorted((Path.home() / ".jdks").glob("*17*"), reverse=True))
    for candidate in candidates:
        executable = candidate / "bin" / "java"
        if executable.is_file() and java_major(executable) in {17, 18, 19, 20, 21}:
            return candidate
    return None


def android_tool(name: str) -> str | None:
    direct = shutil.which(name)
    if direct:
        return direct
    sdk = android_sdk()
    candidate = sdk / "platform-tools" / name if sdk else None
    return str(candidate) if candidate and candidate.is_file() else None


def doctor() -> dict[str, Any]:
    value = base("android doctor")
    value["repositories"] = [repo()]
    for name in ("java", "adb"):
        path = android_tool(name)
        outcome = "pass" if path else "blocked"
        value["checks"].append(item(f"android.tool.{name}", outcome, f"{name} available", path or "not found", category=None if path else "missing_tool"))
    wrapper = ROOT / "gradlew"
    value["checks"].append(item("android.gradle.wrapper", "pass" if wrapper.is_file() else "blocked", "repository Gradle wrapper", "found" if wrapper.is_file() else "missing", category=None if wrapper.is_file() else "environment_missing"))
    sdk = android_sdk()
    value["checks"].append(item("android.sdk.config", "pass" if sdk else "blocked", "Android SDK configured", "SDK directory resolved" if sdk else "not configured", category=None if sdk else "environment_missing"))
    value["devices"] = adb_devices()
    selected_java = compatible_java_home()
    value["checks"].append(item("android.jdk.compatible", "pass" if selected_java else "blocked", "JDK 17-21 available for AGP", str(selected_java) if selected_java else "no compatible JDK found", category=None if selected_java else "version_mismatch"))
    value["environment"] = {
        "platform": "android",
        "software_tools": [
            {"name": "gradle-wrapper", "version": "8.11.1", "source": "gradle-wrapper.properties"},
            {"name": "android-gradle-plugin", "version": "8.9.2", "source": "gradle/libs.versions.toml"},
            {"name": "java", "version": str(java_major(selected_java / "bin" / "java")) if selected_java else "unavailable", "source": "selected JDK"},
        ],
        "read_only": True,
    }
    return finish(value)


def verify(scenario: str, device_json: str | None) -> dict[str, Any]:
    level = next((entry["level"] for entry in SCENARIOS if entry["id"] == scenario), "host")
    value = base(f"android verify {scenario}", level)
    value["scenario"] = scenario
    value["scenario_version"] = 1
    value["repositories"] = [repo()]
    selected_java = compatible_java_home()
    value["environment"] = {
        "platform": "android",
        "software_tools": [
            {"name": "gradle-wrapper", "version": "8.11.1", "source": "gradle-wrapper.properties"},
            {"name": "android-gradle-plugin", "version": "8.9.2", "source": "gradle/libs.versions.toml"},
            {"name": "java", "version": str(java_major(selected_java / "bin" / "java")) if selected_java else "unavailable", "source": "selected JDK"},
        ],
    }
    if scenario == "ANDROID-RECONNECT-001":
        value["checks"].append(item("android.reconnect", "blocked", "automated reconnect and no-resume assertion", "no instrumentation fixture currently controls gateway loss and observes Pico admission", category="human_action_required"))
        value["safe_next_action"] = "Add the integration fixture before claiming reconnect behavior."
        return finish(value)
    tasks = ["assembleDebug", "testDebugUnitTest", "lintDebug"] if scenario == "android-default" else ["installDebug", "connectedDebugAndroidTest"]
    device_env: dict[str, str] = {}
    if scenario != "android-default":
        if not device_json:
            value["checks"].append(item("android.device", "blocked", "resolved ADB device", "device metadata was not supplied", category="device_absent"))
            return finish(value)
        device_data = json.loads(Path(device_json).read_text(encoding="utf-8"))
        device_env["ANDROID_SERIAL"] = device_data["observed"]["stable_id"]
    for task in tasks:
        completed, duration = run([str(ROOT / "gradlew"), task], extra_env=device_env)
        observed = (completed.stdout + "\n" + completed.stderr).strip() or f"exit {completed.returncode}"
        environment_failure = (
            "Downloading https://services.gradle.org" in observed
            and ("Operation not permitted" in observed or "UnknownHostException" in observed)
        ) or "Could not determine a usable wildcard IP" in observed
        outcome = "pass" if completed.returncode == 0 else "blocked" if environment_failure else "fail"
        category = None if outcome == "pass" else "environment_missing" if outcome == "blocked" else "build_failed"
        value["checks"].append(item(f"android.gradle.{task}", outcome, f"Gradle {task} succeeds", observed, duration, category, completed.returncode, ["./gradlew", task]))
        if outcome == "blocked":
            value["safe_next_action"] = "Provide the pinned Gradle distribution/dependencies or allow network access, then rerun."
        if outcome == "fail":
            break
    value["devices"] = adb_devices()
    return finish(value)


def main() -> int:
    as_json = "--json" in sys.argv
    argv = [arg for arg in sys.argv[1:] if arg != "--json"]
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("describe")
    sub.add_parser("doctor")
    sub.add_parser("inventory")
    verify_parser = sub.add_parser("verify")
    verify_parser.add_argument("scenario")
    verify_parser.add_argument("--device-json")
    args = parser.parse_args(argv)
    value = describe() if args.command == "describe" else doctor() if args.command in {"doctor", "inventory"} else verify(args.scenario, args.device_json)
    print(json.dumps(value, indent=2, sort_keys=True) if as_json else f"{value['command']}: {value['outcome']}")
    return 0 if value["outcome"] == "pass" else 20 if value["outcome"] == "blocked" else 10


if __name__ == "__main__":
    raise SystemExit(main())
