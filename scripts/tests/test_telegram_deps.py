from __future__ import annotations

import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import telegram_deps  # noqa: E402
import telegramlib  # noqa: E402
from telegramlib import Dependency  # noqa: E402


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


def _git(cwd: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", "-C", str(cwd), *args],
        capture_output=True,
        text=True,
        env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
    )
    if proc.returncode != 0:
        raise AssertionError(f"git {args} failed: {proc.stderr.strip()}")
    return proc.stdout


class PinnedPlanTest(unittest.TestCase):
    """The plan comes from the pinned gitlinks, not from a README or manifest."""

    @requires_history
    def test_fifteen_dependencies_with_pinned_shas(self) -> None:
        plan = telegramlib.dependency_plan(ROOT)
        self.assertEqual(15, len(plan))
        by_name = {dep.name: dep for dep in plan}
        expected = {
            "TMessagesProj/jni/td": (
                "022d60202e446ad1287b9fb68e687c8a0760788b",
                "https://github.com/tdlib/td.git",
            ),
            "TMessagesProj_Modules/media": (
                "c430d207677071b1873f9f18266d55ec45722180",
                "https://github.com/Arseny271/media.git",
            ),
            "TMessagesProj/lib/jlatexmath": (
                "919e50b2f6f64b04b712cdb13d558ff9ecf9c8ed",
                "https://github.com/dkaraush/jlatexmath-android.git",
            ),
            "TMessagesProj/jni/third_party/libyuv": (
                "28ce69c2744a6aafdb58564e7b884aec3f66be5f",
                "https://chromium.googlesource.com/libyuv/libyuv",
            ),
            "TMessagesProj/jni/third_party/wamr": (
                "25bd7eb63e828e4bd242cc9b38d260b4b31c6605",
                "https://github.com/wasm-micro-runtime/wasm-micro-runtime.git",
            ),
        }
        for name, (sha, url) in expected.items():
            self.assertIn(name, by_name)
            self.assertEqual(sha, by_name[name].sha, name)
            self.assertEqual(url, by_name[name].url, name)

    @requires_history
    def test_plan_matches_the_gitlinks_tracked_in_head(self) -> None:
        out = _git(ROOT, "ls-tree", "-r", "HEAD", "--", telegramlib.SUBTREE_PREFIX)
        head = {}
        for line in out.splitlines():
            meta, _, path = line.partition("\t")
            parts = meta.split()
            if parts[0] == "160000":
                head[path] = parts[2]
        plan = {dep.path: dep.sha for dep in telegramlib.dependency_plan(ROOT)}
        self.assertEqual(head, plan)

    def test_wrong_pin_fails(self) -> None:
        with self.assertRaises(telegramlib.TelegramPinError):
            telegramlib.dependency_plan(ROOT, base="0" * 40)

    @requires_history
    def test_cli_plan_prints_every_dependency(self) -> None:
        out = io.StringIO()
        with redirect_stdout(out):
            code = telegram_deps.main(["--root", str(ROOT), "--plan"])
        self.assertEqual(0, code)
        lines = [ln for ln in out.getvalue().splitlines() if ln.strip()]
        self.assertEqual(15, len(lines))
        self.assertTrue(all(ln.count("\t") == 2 for ln in lines))


class OfflineVerifyTest(unittest.TestCase):
    def _plan(self, root: Path, sha: str = "a" * 40) -> list:
        return [Dependency(name="dep/a", path="apps/android/telegram/dep/a", url="u", sha=sha)]

    def test_missing_dependency_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            problems = telegramlib.verify_dependencies(Path(tmp), self._plan(Path(tmp)))
        self.assertEqual(1, len(problems))
        self.assertIn("missing dependency", problems[0])

    def test_unverified_content_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dest = root / "apps/android/telegram/dep/a"
            dest.mkdir(parents=True)
            (dest / "file.c").write_text("int x;\n")
            problems = telegramlib.verify_dependencies(root, self._plan(root))
        self.assertEqual(1, len(problems))
        self.assertIn("unverified dependency", problems[0])

    def test_stale_pin_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dest = root / "apps/android/telegram/dep/a"
            dest.mkdir(parents=True)
            (dest / "file.c").write_text("int x;\n")
            (dest / telegramlib.DEP_STAMP).write_text("b" * 40 + "\n")
            problems = telegramlib.verify_dependencies(root, self._plan(root))
        self.assertEqual(1, len(problems))
        self.assertIn("wrong pinned dependency", problems[0])

    def test_pinned_content_verifies(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dest = root / "apps/android/telegram/dep/a"
            dest.mkdir(parents=True)
            (dest / "file.c").write_text("int x;\n")
            (dest / telegramlib.DEP_STAMP).write_text("a" * 40 + "\n")
            self.assertEqual([], telegramlib.verify_dependencies(root, self._plan(root)))

    def test_cli_verify_fails_on_a_wrong_pin(self) -> None:
        out = io.StringIO()
        with redirect_stdout(out):
            code = telegram_deps.main(
                ["--root", str(ROOT), "--verify", "--base", "0" * 40]
            )
        self.assertEqual(1, code)


class MaterializeTest(unittest.TestCase):
    """A local `file://` remote proves fetch+archive and idempotence offline."""

    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="telegram-deps-e2e-"))
        self.origin = self.tmp / "origin"
        self.origin.mkdir()
        common = ["-c", "user.email=test@example.com", "-c", "user.name=test"]
        _git(self.origin, "init", "-q")
        (self.origin / "hello.c").write_text("int hello;\n")
        _git(self.origin, "add", "hello.c")
        _git(self.origin, *common, "commit", "-q", "-m", "origin")
        self.sha = _git(self.origin, "rev-parse", "HEAD").strip()
        self.root = self.tmp / "repo"
        self.dep = Dependency(
            name="dep/a",
            path="apps/android/telegram/dep/a",
            url="file://" + str(self.origin),
            sha=self.sha,
        )
        self.dest = self.root / self.dep.path

    def tearDown(self) -> None:
        import shutil

        shutil.rmtree(str(self.tmp), ignore_errors=True)

    def test_materialize_then_idempotent_skip(self) -> None:
        failures = telegram_deps.materialize(
            self.root, [self.dep], timeout=60, log=lambda _m: None
        )
        self.assertEqual(0, failures)
        self.assertEqual("int hello;\n", (self.dest / "hello.c").read_text())
        self.assertEqual(self.sha, (self.dest / telegramlib.DEP_STAMP).read_text().strip())

        # A second run must not touch the network or rewrite the tree.
        stamp = self.dest / telegramlib.DEP_STAMP
        stamp.write_text(self.sha + "\n")  # keep it valid, watch mtime
        before = stamp.stat().st_mtime_ns
        failures = telegram_deps.materialize(
            self.root, [self.dep], timeout=60, log=lambda _m: None
        )
        self.assertEqual(0, failures)
        self.assertEqual(before, stamp.stat().st_mtime_ns)

    def test_stale_stamp_triggers_a_refetch(self) -> None:
        telegram_deps.materialize(self.root, [self.dep], timeout=60, log=lambda _m: None)
        (self.dest / telegramlib.DEP_STAMP).write_text("c" * 40 + "\n")
        failures = telegram_deps.materialize(
            self.root, [self.dep], timeout=60, log=lambda _m: None
        )
        self.assertEqual(0, failures)
        self.assertEqual(self.sha, (self.dest / telegramlib.DEP_STAMP).read_text().strip())

    def test_missing_remote_commit_is_a_failure_not_a_crash(self) -> None:
        bad = self.dep._replace(sha="f" * 40)
        failures = telegram_deps.materialize(
            self.root, [bad], timeout=60, log=lambda _m: None
        )
        self.assertEqual(1, failures)


if __name__ == "__main__":
    unittest.main()
