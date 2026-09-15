from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from vendorlib import (  # noqa: E402
    changed_since_head,
    changed_with_head_source,
    comment_only_edit,
    strip_comments,
)

SOURCE = """class A {
    // old comment
    int x = 1;
}
"""


def git(cwd: Path, *args: str) -> None:
    subprocess.run(["git", *args], cwd=str(cwd), check=True, capture_output=True)


class StripCommentsTest(unittest.TestCase):
    def test_line_and_block_comments_are_removed(self) -> None:
        self.assertEqual("inta=1;", strip_comments("int a = 1; // trailing\n"))
        self.assertEqual("inta=1;", strip_comments("/* leading */ int a = 1;"))

    def test_string_literals_survive(self) -> None:
        out = strip_comments('String url = "https://example.com/a"; // note\n')
        self.assertIn('"https://example.com/a"', out)
        self.assertNotIn("note", out)


class ChangeSetTest(unittest.TestCase):
    """Rename-aware change detection: moving a file is not editing it."""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        git(self.root, "init", "-q")
        git(self.root, "config", "user.email", "t@example.invalid")
        git(self.root, "config", "user.name", "t")
        (self.root / "A.java").write_text(SOURCE, encoding="utf-8")
        git(self.root, "add", "A.java")
        git(self.root, "commit", "-qm", "init")

    def test_pure_rename_is_not_a_content_change(self) -> None:
        git(self.root, "mv", "A.java", "B.java")
        self.assertEqual({}, changed_with_head_source(self.root))
        self.assertEqual(set(), changed_since_head(self.root))

    def test_moved_file_maps_back_to_its_history(self) -> None:
        git(self.root, "mv", "A.java", "B.java")
        (self.root / "B.java").write_text(SOURCE.replace("old comment", "new comment"), encoding="utf-8")
        self.assertEqual({"B.java": "A.java"}, changed_with_head_source(self.root))
        self.assertTrue(comment_only_edit(self.root, "B.java", "A.java"))

    def test_moved_and_edited_file_is_a_real_change(self) -> None:
        git(self.root, "mv", "A.java", "B.java")
        (self.root / "B.java").write_text(SOURCE.replace("int x = 1", "int x = 2"), encoding="utf-8")
        self.assertFalse(comment_only_edit(self.root, "B.java", "A.java"))
        self.assertIn("B.java", changed_since_head(self.root))

    def test_comment_only_edit_in_place(self) -> None:
        (self.root / "A.java").write_text(SOURCE.replace("old comment", "new comment"), encoding="utf-8")
        self.assertEqual({"A.java": "A.java"}, changed_with_head_source(self.root))
        self.assertTrue(comment_only_edit(self.root, "A.java", "A.java"))

    def test_new_file_has_no_history_to_compare(self) -> None:
        (self.root / "New.java").write_text(SOURCE, encoding="utf-8")
        git(self.root, "add", "New.java")
        sources = changed_with_head_source(self.root)
        self.assertIn("New.java", sources)
        self.assertIsNone(sources["New.java"])
        self.assertFalse(comment_only_edit(self.root, "New.java", None))


if __name__ == "__main__":
    unittest.main()
