"""Host-driven process termination check; use a dedicated emulator with the test APK installed."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = "com.miuing.reader"
TITLE = "MIUing-process-restore-fixture"


def verify(adb_path, serial, output, inject_seed_failure=False):
    output.mkdir(parents=True, exist_ok=True)
    token = uuid.uuid4().hex
    title = f"{TITLE}-{token}"

    def adb(*args, check=True):
        result = subprocess.run([str(adb_path), "-s", serial, *args], capture_output=True, timeout=40)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", "replace") or result.stdout.decode("utf-8", "replace"))
        return result.stdout.decode("utf-8", "replace")

    def fixture(mode):
        result = adb("shell", "am", "instrument", "-w", "-e", "processFixtureMode", mode,
                     "-e", "processFixtureToken", token,
                     "-e", "processFixtureFailAfterImport", str(inject_seed_failure and mode == "seed").lower(),
                     "-e", "class", f"{PACKAGE}.EpubProcessRestoreFixture#{mode}",
                     f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner")
        (output / f"{mode}.log").write_text(result, encoding="utf-8")
        if "OK (1 test)" not in result:
            raise AssertionError(f"Fixture {mode} failed: {result}")

    def record():
        index = json.loads(adb("shell", "run-as", PACKAGE, "cat", "files/library/index.json"))
        return next(book for book in index["books"] if book["title"] == title)

    def hierarchy():
        adb("shell", "uiautomator", "dump", "/data/local/tmp/miuing-process-restore.xml")
        return adb("shell", "cat", "/data/local/tmp/miuing-process-restore.xml")

    def open_and_verify(stage):
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        deadline = time.monotonic() + 30
        last = ""
        clicked = False
        while time.monotonic() < deadline:
            try:
                last = hierarchy()
                root = ET.fromstring(last)
                for node in root.iter("node"):
                    if not clicked and node.get("text") == title:
                        a, b, c, d = map(int, re.findall(r"\d+", node.get("bounds")))
                        adb("shell", "input", "tap", str((a+c)//2), str((b+d)//2))
                        clicked = True
                        break
                else:
                    passage = [node for node in root.iter("node") if "Reading passage 320" in node.get("text", "")
                               and len(node.get("text", "")) < 512]
                    window = next(root.iter("node"))
                    window_bounds = list(map(int, re.findall(r"\d+", window.get("bounds", ""))))
                    def visible(node):
                        bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                        if len(bounds) != 4 or len(window_bounds) != 4:
                            return False
                        a, b, c, d = bounds
                        return c > a and d > b and c > window_bounds[0] and d > window_bounds[1] and a < window_bounds[2] and b < window_bounds[3]
                    if any(visible(node) for node in passage):
                        (output / f"{stage}.xml").write_text(last, encoding="utf-8")
                        return record()
            except (RuntimeError, ET.ParseError):
                pass
            time.sleep(.2)
        (output / f"{stage}-failed.xml").write_text(last, encoding="utf-8")
        raise AssertionError("Expected saved passage was not visible in the reader")

    # Check ownership before entering cleanup: a pre-existing title is never ours to delete.
    existing = adb("shell", "run-as", PACKAGE, "cat", "files/library/index.json", check=False)
    if existing.strip() and any(book["title"] == title for book in json.loads(existing)["books"]):
        raise AssertionError("An existing book uses this fixture ownership token")
    try:
        fixture("seed")
        expected = record()
        before = open_and_verify("before-kill")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        adb("shell", "am", "force-stop", PACKAGE)
        if adb("shell", "pidof", PACKAGE, check=False).strip():
            raise AssertionError("Application process still exists")
        after = open_and_verify("after-kill")
        for key in ("id", "chapter", "anchor", "marks", "notes"):
            if expected[key] != before[key] or expected[key] != after[key]:
                raise AssertionError(f"Process restoration changed {key}")
        if abs(before["position"] - after["position"]) > .01:
            raise AssertionError("Restored scroll/page fraction differs from the original layout")
        report = {"serial": serial, "processEnded": True, "passageVisibleAfterRestart": True,
                  "id": after["id"], "anchor": after["anchor"], "bookmarks": len(after["marks"]),
                  "notes": len(after["notes"]), "positionBefore": before["position"], "positionAfter": after["position"]}
        (output / "result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report))
    finally:
        adb("shell", "am", "force-stop", PACKAGE)
        fixture("cleanup")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--inject-seed-failure", action="store_true", help="Verify owned cleanup when the import fixture fails")
    args = parser.parse_args()
    verify(args.adb.resolve(), args.serial, args.output.resolve(), args.inject_seed_failure)
