from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from vendor_diff import resolve_entry  # noqa: E402


class VendorDiffResolveEntryTest(unittest.TestCase):
    def test_exact_local_path_wins_over_shared_upstream_suffix(self) -> None:
        manifest = {
            "local_root": "apps/android/",
            "upstream": {"pinned_commit": "a" * 40},
            "files": [
                {
                    "local": "telegram/src/main/java/org/telegram/ui/ArticleViewer.java",
                    "upstream": "org/telegram/ui/ArticleViewer.java",
                },
                {
                    "local": "app/src/main/java/example/TopicHeaderCell.java",
                    "upstream": "org/telegram/ui/ArticleViewer.java",
                },
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            resolved = resolve_entry(
                manifest,
                "apps/android/telegram/src/main/java/org/telegram/ui/ArticleViewer.java",
                Path(directory),
            )

        self.assertEqual(
            "telegram/src/main/java/org/telegram/ui/ArticleViewer.java",
            resolved["local"],
        )


if __name__ == "__main__":
    unittest.main()
