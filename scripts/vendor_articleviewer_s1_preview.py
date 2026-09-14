#!/usr/bin/env python3
"""Phase 1: ArticleViewer S1 reverse-patch preview (does NOT write the main file).

Applies the same TL_iv → TLRPC mapping used for WebInstantView to a pin copy of
ArticleViewer, reports remaining symbols / missing TLRPC types, and optionally
writes a working copy under /tmp for inspection.

Usage:
  scripts/vendor_articleviewer_s1_preview.py
  scripts/vendor_articleviewer_s1_preview.py --write /tmp/ArticleViewer-s1.java
  scripts/vendor_articleviewer_s1_preview.py --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    git_show,
    load_index,
    load_overlay,
    normalize_sha,
    repo_root,
)

# Shared with WebInstantView S1 (keep in sync when extending).
TYPE_MAP: dict[str, str] = {
    "Page": "Page",
    "PageBlock": "PageBlock",
    "PageCaption": "TL_pageCaption",
    "PageListItem": "PageListItem",
    "PageListOrderedItem": "PageListOrderedItem",
    "RichText": "RichText",
    "TL_page": "TL_page",
    "TL_pageListItemBlocks": "TL_pageListItemBlocks",
    "TL_pageListItemText": "TL_pageListItemText",
    "TL_pageListOrderedItemBlocks": "TL_pageListOrderedItemBlocks",
    "TL_pageListOrderedItemText": "TL_pageListOrderedItemText",
    "TL_pagePart_layer82": "TL_pagePart_layer82",
    "pageBlockAnchor": "TL_pageBlockAnchor",
    "pageBlockAudio": "TL_pageBlockAudio",
    "pageBlockAuthorDate": "TL_pageBlockAuthorDate",
    "pageBlockBlockquote": "TL_pageBlockBlockquote",
    "pageBlockChannel": "TL_pageBlockChannel",
    "pageBlockCollage": "TL_pageBlockCollage",
    "pageBlockCover": "TL_pageBlockCover",
    "pageBlockDetails": "TL_pageBlockDetails",
    "pageBlockDivider": "TL_pageBlockDivider",
    "pageBlockEmbed": "TL_pageBlockEmbed",
    "pageBlockEmbedPost": "TL_pageBlockEmbedPost",
    "pageBlockFooter": "TL_pageBlockFooter",
    "pageBlockHeader": "TL_pageBlockHeader",
    "pageBlockHeading1": "TL_pageBlockHeading1",
    "pageBlockHeading2": "TL_pageBlockHeading2",
    "pageBlockHeading3": "TL_pageBlockHeading3",
    "pageBlockHeading4": "TL_pageBlockHeading4",
    "pageBlockHeading5": "TL_pageBlockHeading5",
    "pageBlockHeading6": "TL_pageBlockHeading6",
    "pageBlockKicker": "TL_pageBlockKicker",
    "pageBlockList": "TL_pageBlockList",
    "pageBlockMap": "TL_pageBlockMap",
    "pageBlockOrderedList": "TL_pageBlockOrderedList",
    "pageBlockParagraph": "TL_pageBlockParagraph",
    "pageBlockPhoto": "TL_pageBlockPhoto",
    "pageBlockPreformatted": "TL_pageBlockPreformatted",
    "pageBlockPullquote": "TL_pageBlockPullquote",
    "pageBlockRelatedArticles": "TL_pageBlockRelatedArticles",
    "pageBlockSlideshow": "TL_pageBlockSlideshow",
    "pageBlockSubheader": "TL_pageBlockSubheader",
    "pageBlockSubtitle": "TL_pageBlockSubtitle",
    "pageBlockTable": "TL_pageBlockTable",
    "pageBlockTitle": "TL_pageBlockTitle",
    "pageBlockUnsupported": "TL_pageBlockUnsupported",
    "pageBlockVideo": "TL_pageBlockVideo",
    "pageRelatedArticle": "TL_pageRelatedArticle",
    "pageTableCell": "TL_pageTableCell",
    "pageTableRow": "TL_pageTableRow",
    "textAnchor": "TL_textAnchor",
    "textBold": "TL_textBold",
    "textConcat": "TL_textConcat",
    "textEmail": "TL_textEmail",
    "textEmpty": "TL_textEmpty",
    "textFixed": "TL_textFixed",
    "textImage": "TL_textImage",
    "textItalic": "TL_textItalic",
    "textMarked": "TL_textMarked",
    "textPhone": "TL_textPhone",
    "textPlain": "TL_textPlain",
    "textStrike": "TL_textStrike",
    "textSubscript": "TL_textSubscript",
    "textSuperscript": "TL_textSuperscript",
    "textUnderline": "TL_textUnderline",
    "textUrl": "TL_textUrl",
    # Absent on local TLRPC — map to closest existing types for preview only:
    "pageBlockMath": "TL_pageBlockUnsupported",
    "textMath": "TL_textPlain",
    # A3: local TLRPC.TL_textSpoiler exists; pin TL_iv.textSpoiler → spoiler span, not mark highlight.
    "textSpoiler": "TL_textSpoiler",
    "RichMessage": "RichText",
    # A6: nested multi-block quotes now have a local type.
    "pageBlockBlockquoteBlocks": "TL_pageBlockBlockquoteBlocks",
}
def apply_s1(pin: str) -> tuple[str, list[str]]:
    text = pin
    text = text.replace("import org.telegram.tgnet.tl.TL_iv;\n", "")
    if "import org.telegram.tgnet.TLRPC;" not in text:
        text = text.replace(
            "package org.telegram.ui;\n",
            "package org.telegram.ui;\n\nimport org.telegram.tgnet.TLRPC;\n",
            1,
        )
    for src in sorted(TYPE_MAP.keys(), key=len, reverse=True):
        text = text.replace(f"TL_iv.{src}", f"TLRPC.{TYPE_MAP[src]}")
    left = sorted(set(re.findall(r"TL_iv\.([A-Za-z0-9_]+)", text)))
    return text, left


def tlrpc_has(name: str, tlrpc: str) -> bool:
    return (
        f"class {name} " in tlrpc
        or f"class {name}\n" in tlrpc
        or f"class {name}{{" in tlrpc
        or f"static class {name}" in tlrpc
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", metavar="PATH", help="write mapped pin copy (not main tree)")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    root = repo_root()
    overlay_pin = normalize_sha(load_overlay(root)["upstream"]["pinned_commit"])
    pin = git_show(
        overlay_pin,
        "TMessagesProj/src/main/java/org/telegram/ui/ArticleViewer.java",
    )
    if not pin:
        print("ERROR: cannot load pin ArticleViewer", file=sys.stderr)
        return 1
    pin_t = pin.decode("utf-8", errors="replace")
    mapped, remaining = apply_s1(pin_t)

    refs = re.findall(r"TL_iv\.([A-Za-z0-9_]+)", pin_t)
    ref_counts = Counter(refs)
    tlrpc_path = root / "apps/android/telegram/src/main/java/org/telegram/tgnet/TLRPC.java"
    tlrpc = tlrpc_path.read_text(encoding="utf-8", errors="replace")

    mapped_ok = []
    mapped_miss_tlrpc = []
    for src, dst in sorted(TYPE_MAP.items()):
        if not tlrpc_has(dst, tlrpc):
            mapped_miss_tlrpc.append({"tl_iv": src, "target": dst})
        else:
            mapped_ok.append({"tl_iv": src, "target": dst, "refs_in_pin": ref_counts.get(src, 0)})

    unmapped_in_pin = sorted(set(refs) - set(TYPE_MAP.keys()))

    # Local host markers to re-apply later (informational)
    local = (
        root
        / "apps/android/telegram/src/main/java/org/telegram/ui/ArticleViewer.java"
    ).read_text(encoding="utf-8", errors="replace")
    host_hits = []
    for i, line in enumerate(local.splitlines(), 1):
        if any(
            k in line
            for k in (
                "NativeDo",
                "WebBookmarks",
                "getActionBarLayoutActivity",
                "OMITTED",
                "ADAPTED",
            )
        ):
            if line.strip().startswith("import "):
                continue
            host_hits.append({"line": i, "text": line.strip()[:160]})

    report = {
        "pin": overlay_pin,
        "pin_lines": pin_t.count("\n"),
        "mapped_lines": mapped.count("\n"),
        "tl_iv_refs_in_pin": len(refs),
        "tl_iv_unique_in_pin": len(set(refs)),
        "remaining_tl_iv_after_map": remaining,
        "unmapped_types_in_pin": unmapped_in_pin,
        "map_targets_missing_in_tlrpc": mapped_miss_tlrpc,
        "map_ok_count": len(mapped_ok),
        "host_marker_hits_in_local": host_hits[:50],
        "host_marker_hit_count": len(host_hits),
        "top_tl_iv_types": ref_counts.most_common(25),
    }

    if args.write:
        out = Path(args.write)
        out.write_text(mapped, encoding="utf-8")
        report["written"] = str(out)

    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
    else:
        print("=== ArticleViewer S1 preview (no main-tree write) ===")
        print(f"pin {overlay_pin[:12]}  lines={report['pin_lines']}  TL_iv refs={report['tl_iv_refs_in_pin']}")
        print(f"after map: remaining TL_iv types={remaining or '[]'}")
        print(f"unmapped types present in pin: {unmapped_in_pin or '[]'}")
        print(f"map targets missing from local TLRPC: {mapped_miss_tlrpc or '[]'}")
        print(f"local host-marker hits: {report['host_marker_hit_count']}")
        for h in host_hits[:15]:
            print(f"  L{h['line']}: {h['text']}")
        if host_hits[15:]:
            print(f"  ... +{len(host_hits) - 15}")
        print("top TL_iv types in pin:")
        for name, c in report["top_tl_iv_types"][:15]:
            target = TYPE_MAP.get(name, "?")
            print(f"  {c:4d} TL_iv.{name} -> TLRPC.{target}")
        if args.write:
            print(f"wrote {args.write}")
        print("OK" if not remaining and not mapped_miss_tlrpc else "NEEDS_MAP_EXTENSION")

    # Exit 0 even if extension needed — this is a report tool
    return 0


if __name__ == "__main__":
    sys.exit(main())
