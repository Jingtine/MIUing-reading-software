"""Check repository Markdown file links without fetching external URLs.

Run from any directory: python tools/docs/check_links.py
"""
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
LINK = re.compile(r"!?\[[^\]\n]*\]\(([^)\n]+)\)")


def main():
    tracked = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "*.md"], cwd=ROOT
    ).decode("utf-8").split("\0")
    paths = {ROOT / name for name in tracked if name}
    for directory in ("docs", "tools", "design"):
        paths.update((ROOT / directory).rglob("*.md"))
    broken = []
    checked = 0
    for path in sorted(paths):
        if not path.is_file():
            continue  # A tracked document may have just moved before staging.
        for match in LINK.finditer(path.read_text(encoding="utf-8-sig")):
            target = match.group(1).strip().split(' "', 1)[0].strip("<>")
            if re.match(r"^[\w+.-]+:", target) or target.startswith(("#", "/")):
                continue
            target = unquote(target.split("#", 1)[0].split("?", 1)[0])
            if target:
                checked += 1
                if not (path.parent / target).exists():
                    broken.append(f"{path.relative_to(ROOT).as_posix()}: {target}")
    for entry in broken:
        print(entry)
    print(f"Checked {checked} local Markdown links; {len(broken)} broken.")
    return bool(broken)


if __name__ == "__main__":
    sys.exit(main())
