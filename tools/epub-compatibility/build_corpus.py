"""Build independent EPUB fixtures from exact upstream Git blobs, never working-tree filters.

Clone IDPF/epub3-samples at REVISION, sparse-checkout the selected directories,
then pass --checkout and --output. Sample bytes remain local and are not shipped.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

REVISION = "7651e2002b631e6577fadf7e9e0692fa6efb8746"
SAMPLES = ("wasteland-otf", "wasteland-otf-obf", "wasteland-woff", "wasteland-woff-obf",
           "moby-dick", "internallinks", "childrens-literature", "epub30-spec",
           "childrens-media-query")


def build(checkout, output):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(checkout), *args])
    if git("rev-parse", "HEAD").decode().strip() != REVISION:
        raise ValueError("Checkout must match the pinned upstream revision")
    output.mkdir(parents=True, exist_ok=True)
    tree = git("ls-tree", "-rz", "HEAD", "--", *(f"30/{name}/" for name in SAMPLES))
    blobs = {}
    process = subprocess.Popen(["git", "-C", str(checkout), "cat-file", "--batch"],
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for entry in tree.split(b"\0"):
            if not entry:
                continue
            metadata, path = entry.split(b"\t", 1)
            _, kind, sha = metadata.split()
            if kind != b"blob":
                raise ValueError("Unexpected non-file corpus entry")
            process.stdin.write(sha + b"\n")
            process.stdin.flush()
            header = process.stdout.readline().split()
            if len(header) != 3 or header[:2] != [sha, b"blob"]:
                raise ValueError("Missing pinned Git blob")
            size = int(header[2])
            data = process.stdout.read(size)
            if process.stdout.read(1) != b"\n" or len(data) != size:
                raise ValueError("Incomplete Git blob")
            actual = hashlib.sha1(b"blob " + str(size).encode() + b"\0" + data).hexdigest()
            if actual != sha.decode():
                raise ValueError("Git blob checksum mismatch")
            blobs[path.decode()] = data
    finally:
        process.stdin.close()
        process.stdout.close()
        process.wait(timeout=20)
    manifest = {"repository": "https://github.com/IDPF/epub3-samples", "revision": REVISION,
                "license": "CC-BY-SA-3.0 unless sample metadata specifies otherwise; fonts retain their own licenses",
                "books": [], "fontPairs": []}
    for sample in SAMPLES:
        prefix = f"30/{sample}/"
        entries = {path[len(prefix):]: data for path, data in blobs.items() if path.startswith(prefix)}
        if entries.get("mimetype") != b"application/epub+zip":
            raise ValueError(f"Missing EPUB mimetype: {sample}")
        destination = output / f"{sample}.epub"
        with zipfile.ZipFile(destination, "w") as archive:
            for path in ["mimetype", *sorted(set(entries) - {"mimetype"})]:
                info = zipfile.ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_STORED if path == "mimetype" else zipfile.ZIP_DEFLATED
                archive.writestr(info, entries[path])
        manifest["books"].append({"name": sample, "file": destination.name, "entries": len(entries),
                                  "bytes": destination.stat().st_size,
                                  "sha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
        if sample.endswith("-obf"):
            for path, data in entries.items():
                if ".obf." not in path:
                    continue
                plain_path = path.replace(".obf.", ".")
                reference = f"30/{sample[:-4]}/{plain_path}"
                if reference not in blobs:
                    raise ValueError("Independent plain font counterpart missing")
                manifest["fontPairs"].append({"book": sample, "path": path,
                    "reference": reference, "sha256": hashlib.sha256(blobs[reference]).hexdigest(),
                    "bytes": len(data)})
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"revision": REVISION, "books": len(manifest["books"]),
                      "fontPairs": len(manifest["fontPairs"]),
                      "bytes": sum(book["bytes"] for book in manifest["books"])}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkout", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    build(args.checkout.resolve(), args.output.resolve())
