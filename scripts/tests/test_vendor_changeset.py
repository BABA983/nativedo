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
    known_type_names,
    strip_comments,
    strip_type_qualifiers,
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


class StripTypeQualifiersTest(unittest.TestCase):
    """A class that upstream moved keeps comparing equal to its old spelling."""

    def test_qualified_references_collapse(self) -> None:
        self.assertEqual(
            "MessageDrawable d;",
            strip_type_qualifiers("Theme.MessageDrawable d;", {"MessageDrawable"}),
        )
        self.assertEqual(
            "x = new MessageDrawable(MessageDrawable.TYPE_TEXT);",
            strip_type_qualifiers(
                "x = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT);",
                {"MessageDrawable"},
            ),
        )

    def test_unknown_names_and_longest_match_win(self) -> None:
        self.assertEqual("a.b.c", strip_type_qualifiers("a.b.c", {"Other", ""}))
        # `Drawable` must not eat the tail of `MessageDrawable`.
        self.assertEqual(
            "new MessageDrawable()",
            strip_type_qualifiers("new ui.MessageDrawable()", {"Drawable", "MessageDrawable"}),
        )


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

    def test_import_swap_is_not_a_behaviour_change(self) -> None:
        # The shape a package move takes: identical code, one import changed.
        moved = SOURCE.replace(
            "class A {",
            "import org.telegram.ui.recyclerview.Other;\n\nclass A {",
        )
        (self.root / "A.java").write_text(moved, encoding="utf-8")
        self.assertFalse(comment_only_edit(self.root, "A.java", "A.java"))
        self.assertTrue(
            comment_only_edit(self.root, "A.java", "A.java", ignore_imports=True)
        )
        # …but a real edit behind the import is still reported.
        (self.root / "A.java").write_text(
            moved.replace("int x = 1;", "int x = 2;"), encoding="utf-8"
        )
        self.assertFalse(
            comment_only_edit(self.root, "A.java", "A.java", ignore_imports=True)
        )

    def test_new_file_has_no_history_to_compare(self) -> None:
        (self.root / "New.java").write_text(SOURCE, encoding="utf-8")
        git(self.root, "add", "New.java")
        sources = changed_with_head_source(self.root)
        self.assertIn("New.java", sources)
        self.assertIsNone(sources["New.java"])
        self.assertFalse(comment_only_edit(self.root, "New.java", None))

    def test_class_move_is_not_a_behaviour_change(self) -> None:
        # The shape an extraction takes: the qualified reference and the import
        # follow the class, the code does not move.
        before = "class A {\n    Theme.Drawable d;\n    int x = 1;\n}\n"
        after = (
            "import org.telegram.ui.ActionBar.Drawable;\n\n"
            "class A {\n    Drawable d;\n    int x = 1;\n}\n"
        )
        (self.root / "A.java").write_text(before, encoding="utf-8")
        git(self.root, "add", "A.java")
        git(self.root, "commit", "-qm", "before the move")
        (self.root / "A.java").write_text(after, encoding="utf-8")
        self.assertFalse(
            comment_only_edit(self.root, "A.java", "A.java", ignore_imports=True)
        )
        self.assertTrue(
            comment_only_edit(
                self.root,
                "A.java",
                "A.java",
                ignore_imports=True,
                types={"Drawable"},
            )
        )
        # …but a real edit behind the move is still reported.
        (self.root / "A.java").write_text(
            after.replace("int x = 1;", "int x = 2;"), encoding="utf-8"
        )
        self.assertFalse(
            comment_only_edit(
                self.root,
                "A.java",
                "A.java",
                ignore_imports=True,
                types={"Drawable"},
            )
        )


class KnownTypeNamesTest(unittest.TestCase):
    def test_only_java_and_kotlin_sources_contribute(self) -> None:
        index = {
            "files": [{"local": "org/telegram/ui/ActionBar/MessageDrawable.java"}],
            "compat_shims": [
                {"local": "org/telegram/ui/Components/SpannableStringLight.kt"},
                {"local": "res/map-background-stripes.png"},
            ],
        }
        self.assertEqual(
            {"MessageDrawable", "SpannableStringLight"}, known_type_names(index)
        )


if __name__ == "__main__":
    unittest.main()
