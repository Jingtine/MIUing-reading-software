"""Build complete Unicode font shards for WebViews with a 30 MiB SFNT limit.

Development only: fonttools==4.60.1. Original glyph outlines and metrics are
checked against every encoded character; no character subset is discarded.
"""
from pathlib import Path
import hashlib
import json

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "font-sources/kinghwa.ttf"
DESTINATION = ROOT / "app/src/main/assets/fonts"
EXPECTED = "f7fef9fc413e9e2343f0bb432c51cca41c44b8fe37f071dc86b050896ae9f9e2"
RANGES = (("kinghwa-text.ttf", 0, 0x4DFF),
          ("kinghwa-cjk.ttf", 0x4E00, 0x9FFF),
          ("kinghwa-extended.ttf", 0xA000, 0x10FFFF))


def signature(font, name):
    coordinates, endpoints, flags = font["glyf"][name].getCoordinates(font["glyf"])
    return (list(coordinates), list(endpoints), list(flags),
            font["hmtx"].metrics[name], font["vmtx"].metrics[name])


def main():
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == EXPECTED
    original = TTFont(SOURCE, recalcTimestamp=False)
    cmap = original.getBestCmap()
    covered = set()
    report = []
    for filename, low, high in RANGES:
        codes = {code for code in cmap if low <= code <= high}
        font = TTFont(SOURCE, recalcTimestamp=False)
        options = subset.Options()
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.glyph_names = True
        options.notdef_outline = True
        options.layout_features = ["*"]
        options.recalc_timestamp = False
        builder = subset.Subsetter(options)
        builder.populate(unicodes=codes)
        builder.subset(font)
        path = DESTINATION / filename
        font.save(path)
        font.close()
        rebuilt = TTFont(path, recalcTimestamp=False)
        result = rebuilt.getBestCmap()
        assert set(result) == codes, filename
        for code, name in result.items():
            assert signature(original, cmap[code]) == signature(rebuilt, name), hex(code)
        assert not covered.intersection(result)
        covered.update(result)
        assert path.stat().st_size < 30 * 1024 * 1024, filename
        report.append({"file": filename, "unicodeRange": f"U+{low:X}-{high:X}",
                       "characters": len(result), "bytes": path.stat().st_size,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        rebuilt.close()
    assert covered == set(cmap), "A character was lost"
    original.close()
    print(json.dumps({"sourceSha256": EXPECTED, "encodedCharacters": len(covered),
                      "outlinesAndMetrics": "identical for every encoded character",
                      "shards": report}, indent=2))


if __name__ == "__main__":
    main()
