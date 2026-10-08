from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import telegram_hook_guard as guard  # noqa: E402
import telegramlib  # noqa: E402

HOOK = "io.github.baba983.nativedo.hooks.NativeDoHooks"
CHAT = (
    "apps/android/telegram/TMessagesProj/src/main/java/org/telegram/ui/Cells/"
    "ChatMessageCell.java"
)


def _history_available() -> bool:
    try:
        telegramlib.subtree_base_commit(ROOT)
        return True
    except telegramlib.TelegramPinError:
        return False


HISTORY = _history_available()
requires_history = unittest.skipUnless(
    HISTORY, "subtree pin needs full git history (fetch-depth 0)"
)


class JavaHunkAnalysisTest(unittest.TestCase):
    """Every allowed shape, and the render edit that must not pass."""

    def test_hook_import_is_allowed(self) -> None:
        hunks = [(["import " + HOOK + ";"], [])]
        self.assertEqual([], guard.analyze_java_hunks("X.java", hunks))

    def test_single_line_guard_call_is_allowed(self) -> None:
        hunks = [
            (
                [
                    f"        if (!{HOOK}.telegramStorageEnabled()) return; // NativeDo: never open"
                ],
                [],
            )
        ]
        self.assertEqual([], guard.analyze_java_hunks("X.java", hunks))

    def test_wrapping_replacement_is_allowed(self) -> None:
        hunks = [
            (
                [f"        return {HOOK}.getLastFragment(); // NativeDo: our host"],
                ["        return null;"],
            )
        ]
        self.assertEqual([], guard.analyze_java_hunks("X.java", hunks))

    def test_denesting_reindent_is_allowed(self) -> None:
        # Browser.openInTelegramBrowser: a call site replaces an enclosing
        # `if` and the block below it is de-indented.
        added = [
            f"        BottomSheetTabs tabs = {HOOK}.getBottomSheetTabs(); // NativeDo",
            "        if (tabs != null && tabs.tryReopenTab(url) != null) {",
            "            return true;",
            "        }",
        ]
        removed = [
            "        if (LaunchActivity.instance != null) {",
            "            BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();",
            "            if (tabs != null && tabs.tryReopenTab(url) != null) {",
            "                return true;",
            "            }",
        ]
        self.assertEqual([], guard.analyze_java_hunks("X.java", [(added, removed)]))

    def test_guard_block_body_is_allowed(self) -> None:
        # ArticleViewer: a hook condition opens a block that hands off to
        # upstream's own call before the Telegram-only path.
        added = [
            f"        if ({HOOK}.browserHostPresent()) {{ // NativeDo: load directly",
            "            addPageToStack(url, 1);",
            "            return;",
            "        }",
        ]
        self.assertEqual([], guard.analyze_java_hunks("X.java", [(added, [])]))

    def test_malicious_render_branch_edit_fails(self) -> None:
        added = ["        if (x > 0) {", "            invalidate();", "        }"]
        problems = guard.analyze_java_hunks("X.java", [(added, [])])
        self.assertTrue(problems, "a new render branch must be rejected")
        self.assertIn("not a minimal NativeDoHooks callsite", problems[0])

    def test_render_line_inside_a_hook_block_is_rejected(self) -> None:
        added = [
            f"        if ({HOOK}.browserHostPresent()) {{",
            "            flag = false;",  # logic, not a bare hand-off call
            "        }",
        ]
        problems = guard.analyze_java_hunks("X.java", [(added, [])])
        self.assertTrue(problems)

    def test_removed_upstream_line_without_hook_fails(self) -> None:
        problems = guard.analyze_java_hunks("X.java", [(["        int y = 2;"], ["        int x = 1;"])])
        self.assertTrue(problems)

    def test_whitespace_only_reindent_is_not_a_semantic_change(self) -> None:
        # The de-nesting case (Browser) re-indents upstream lines; a hunk whose
        # only change is indentation keeps upstream semantics and is allowed.
        problems = guard.analyze_java_hunks(
            "X.java", [(["        draw();"], ["            draw();"])]
        )
        self.assertEqual([], problems)

    def test_unrelated_deletion_beside_a_hook_call_fails(self) -> None:
        # The old rule allowed ANY removed line as long as the hunk held a hook
        # call; an upstream line dropped beside one is not a hook replacement.
        problems = guard.analyze_java_hunks(
            "X.java",
            [(["        NativeDoHooks.mtprotoEnabled();"], ["        int important = compute();"])],
        )
        self.assertTrue(problems)
        self.assertIn("no hook replacement", problems[0])

    def test_deletion_of_a_call_beside_a_hook_call_fails(self) -> None:
        problems = guard.analyze_java_hunks(
            "X.java",
            [(["        NativeDoHooks.mtprotoEnabled();"], ["        doImportantWork();"])],
        )
        self.assertTrue(problems)

    def test_deletion_replaced_by_a_hook_argument_is_allowed(self) -> None:
        # Wrapping: the removed expression becomes the hook's argument.
        problems = guard.analyze_java_hunks(
            "X.java",
            [
                (
                    ["        return NativeDoHooks.enabled(flag && other);"],
                    ["        return flag && other;"],
                )
            ],
        )
        self.assertEqual([], problems)

    def test_host_lookup_replaced_by_a_hook_is_allowed(self) -> None:
        problems = guard.analyze_java_hunks(
            "X.java",
            [
                (
                    ["        BottomSheetTabs tabs = NativeDoHooks.getBottomSheetTabs();"],
                    [
                        "        BottomSheetTabs tabs = "
                        "LaunchActivity.instance.getBottomSheetTabs();"
                    ],
                )
            ],
        )
        self.assertEqual([], problems)

    def test_hook_line_may_not_carry_a_second_statement(self) -> None:
        problems = guard.analyze_java_hunks(
            "X.java", [(["        NativeDoHooks.mtprotoEnabled(); invalidate();"], [])]
        )
        self.assertTrue(problems)
        self.assertIn("minimal", problems[0])

    def test_second_call_inside_a_hook_block_fails(self) -> None:
        added = [
            "        if (NativeDoHooks.browserHostPresent()) {",
            "            addPageToStack(url, 1);",
            "            invalidate();",
            "        }",
        ]
        problems = guard.analyze_java_hunks("X.java", [(added, [])])
        self.assertTrue(problems)
        self.assertIn("second hand-off call", problems[0])

    def test_computed_return_inside_a_hook_block_fails(self) -> None:
        added = [
            "        if (NativeDoHooks.browserHostPresent()) {",
            "            return compute(x);",
            "        }",
        ]
        problems = guard.analyze_java_hunks("X.java", [(added, [])])
        self.assertTrue(problems)
        self.assertIn("non-hook logic", problems[0])

    def test_handoff_to_an_undeclared_method_fails(self) -> None:
        # A guard block is a hand-off to this file's own method, not a place to
        # invoke arbitrary upstream behavior (`invalidate` is not declared here).
        added = [
            "        if (NativeDoHooks.browserHostPresent()) {",
            "            invalidate();",
            "            return;",
            "        }",
        ]
        text = "class X {\n    private void other() {\n    }\n}\n"
        problems = guard.analyze_java_hunks("X.java", [(added, [])], text)
        self.assertTrue(problems)
        self.assertIn("does not declare", problems[0])

    def test_handoff_to_a_declared_method_is_allowed(self) -> None:
        added = [
            "        if (NativeDoHooks.browserHostPresent()) {",
            "            addPageToStack(url, 1);",
            "            return;",
            "        }",
        ]
        text = (
            "class X {\n"
            "    private void addPageToStack(String url, int order) {\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual([], guard.analyze_java_hunks("X.java", [(added, [])], text))


class BuildHunkAnalysisTest(unittest.TestCase):
    def test_marker_is_required(self) -> None:
        self.assertTrue(guard.analyze_build_hunks("settings.gradle", [(["include ':evil'"], [])]))

    def test_documented_include_is_allowed(self) -> None:
        added = [
            "include ':nativedo'; project(':nativedo').projectDir = "
            "file('../nativedo') // NativeDo"
        ]
        self.assertEqual([], guard.analyze_build_hunks("settings.gradle", [(added, [])]))

    def test_build_removal_is_rejected(self) -> None:
        self.assertTrue(
            guard.analyze_build_hunks("build.gradle", [([], ["apply plugin: 'x'"])])
        )


class PinResolutionTest(unittest.TestCase):
    def test_wrong_pin_fails(self) -> None:
        with self.assertRaises(telegramlib.TelegramPinError):
            guard.check(ROOT, base="0" * 40)

    @requires_history
    def test_current_working_tree_passes(self) -> None:
        report = guard.check(ROOT)
        self.assertEqual([], report.errors, report.errors)


class GuardEndToEndTest(unittest.TestCase):
    """A real `git worktree`: one malicious render edit, then a hook line."""

    def setUp(self) -> None:
        if not HISTORY:
            self.skipTest("needs full git history")
        self.tmp = Path(tempfile.mkdtemp(prefix="hook-guard-e2e-"))
        proc = subprocess.run(
            ["git", "-C", str(ROOT), "worktree", "add", "-q", "--detach", str(self.tmp), "HEAD"],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0:
            self.skipTest(f"cannot create a git worktree: {proc.stderr.strip()}")
        self.file = self.tmp / CHAT

    def tearDown(self) -> None:
        if getattr(self, "tmp", None) and self.tmp.exists():
            subprocess.run(
                ["git", "-C", str(ROOT), "worktree", "remove", "--force", str(self.tmp)],
                capture_output=True,
                text=True,
            )

    def _inject_after_method(self, snippet: str) -> None:
        text = self.file.read_text(encoding="utf-8")
        needle = "    private void updateFlagSecure() {\n"
        self.assertIn(needle, text)
        self.file.write_text(text.replace(needle, needle + snippet, 1), encoding="utf-8")

    def test_malicious_render_edit_breaks_the_guard(self) -> None:
        self._inject_after_method("        canvas.drawRect(0, 0, 1, 1, null);\n")
        report = guard.check(self.tmp)
        self.assertTrue(report.errors, "guard must reject a raw render edit")

    def test_allowed_hook_line_passes_the_guard(self) -> None:
        self._inject_after_method(
            f"        if (!{HOOK}.mtprotoEnabled()) return; // NativeDo: e2e hook\n"
        )
        report = guard.check(self.tmp)
        self.assertEqual([], report.errors, report.errors)

    def test_new_file_at_an_upstream_path_fails(self) -> None:
        shim = (
            self.tmp
            / "apps/android/telegram/TMessagesProj/src/main/java/org/telegram/ui/EvilShim.java"
        )
        shim.write_text("package org.telegram.ui;\nclass EvilShim {}\n")
        report = guard.check(self.tmp)
        self.assertTrue(
            any("at an upstream path" in e for e in report.errors), report.errors
        )

    def _replace_line(self, old: str, new: str) -> None:
        text = self.file.read_text(encoding="utf-8")
        self.assertIn(old, text)
        self.file.write_text(text.replace(old, new, 1), encoding="utf-8")

    def test_unrelated_deletion_beside_a_hook_call_breaks_the_guard(self) -> None:
        # A real upstream line dropped next to a hook call in the same hunk: the
        # hook does not replace it, so the guard must reject the deletion.
        self._replace_line(
            "            Activity activity = AndroidUtilities.findActivity(getContext());\n",
            "            " + HOOK + ".checkSystemBarColors(); // NativeDo: e2e unrelated\n",
        )
        report = guard.check(self.tmp)
        self.assertTrue(
            any("no hook replacement" in e for e in report.errors), report.errors
        )

    def test_hook_call_carrying_a_second_statement_breaks_the_guard(self) -> None:
        self._inject_after_method(
            f"        {HOOK}.checkSystemBarColors(); invalidate(); // NativeDo: e2e\n"
        )
        report = guard.check(self.tmp)
        self.assertTrue(any("minimal" in e for e in report.errors), report.errors)


if __name__ == "__main__":
    unittest.main()
