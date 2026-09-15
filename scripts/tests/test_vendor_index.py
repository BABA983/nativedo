from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from vendorlib import (  # noqa: E402
    VENDOR_DIR_ENV,
    ManifestError,
    load_index,
    load_overlay,
    vendor_dir,
)

UPSTREAM = "TMessagesProj/src/main/java/org/telegram/ui/Foo.java"
LOCAL = "TMessagesProj/src/main/java/org/telegram/ui/Foo.java"

OVERLAY = {
    "schema_version": 1,
    "upstream": {
        "repo": "git@example.invalid/t.git",
        "pinned_commit": "a" * 40,
        "local_checkout": "../telegram",
    },
    "policy": {"sync": "on-demand"},
    "native": ["TMessagesProj/src/main/java/org/telegram/ui/Mine.java"],
    "upstream_ports": {"app/src/main/java/example/Port.kt": "org/telegram/ui/Foo.java"},
    "derived_from": {
        "TMessagesProj/src/main/java/org/telegram/ui/Renamed.java": "org/telegram/ui/Foo.java"
    },
    "reference": {
        "commit": "b" * 40,
        "why": "historical baseline",
        "paths": ["TMessagesProj/src/main/java/org/telegram/ui/Mine.java"],
    },
    "forced_exact": {
        "TMessagesProj/src/main/java/org/telegram/ui/Mine.java": ["measurement", "drawing"]
    },
    "resources": [{"kind": "drawable", "files": ["a.9.png"], "upstream_dir": "res"}],
}


class IndexDerivationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "repo"
        self.root.mkdir()
        patcher = mock.patch.dict(os.environ, {}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        os.environ.pop(VENDOR_DIR_ENV, None)

    # --- overlay loading -------------------------------------------------

    def write_overlay(self, overlay: dict | None = None) -> None:
        (self.root / "vendor-overlay.json").write_text(
            json.dumps(overlay if overlay is not None else OVERLAY, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_overlay_requires_schema_and_pin(self) -> None:
        (self.root / "vendor-overlay.json").write_text("{}\n", encoding="utf-8")
        with self.assertRaises(ManifestError):
            load_overlay(self.root)

    def test_forced_exact_is_validated_and_sorted(self) -> None:
        overlay = json.loads(json.dumps(OVERLAY))
        overlay["forced_exact"] = {LOCAL: ["drawing", "measurement"]}
        self.write_overlay(overlay)
        loaded = load_overlay(self.root)
        self.assertEqual(
            ["drawing", "measurement"], loaded["forced_exact"][LOCAL]
        )

    def test_forced_exact_rejects_unknown_subsystem(self) -> None:
        overlay = json.loads(json.dumps(OVERLAY))
        overlay["forced_exact"] = {LOCAL: ["vibes"]}
        self.write_overlay(overlay)
        with self.assertRaises(ManifestError):
            load_overlay(self.root)

    def test_dead_overlay_path_is_an_error(self) -> None:
        overlay = json.loads(json.dumps(OVERLAY))
        overlay["upstream_ports"] = {}
        overlay["derived_from"] = {}
        overlay["reference"]["paths"] = []
        self.write_overlay(overlay)
        mine = self.root / "apps/android/TMessagesProj/src/main/java/org/telegram/ui/Mine.java"
        with self.assertRaises(ManifestError) as ctx:
            load_index(self.root, with_modified=False)
        self.assertIn("Mine.java", str(ctx.exception))
        mine.parent.mkdir(parents=True)
        mine.write_text("class Mine {}\n", encoding="utf-8")
        # The pristine checkout is still missing: that error names the fix.
        with self.assertRaises(ManifestError) as ctx:
            load_index(self.root, with_modified=False)
        self.assertIn("vendor-setup.sh", str(ctx.exception))

    # --- derivation ------------------------------------------------------

    def make_pristine(self, blobs: dict[str, str]) -> Path:
        """A tiny git repo standing in for the pristine Telegram checkout."""
        import subprocess

        repo = Path(self.tmp.name) / "telegram"
        repo.mkdir(exist_ok=True)
        env = {
            **os.environ,
            "GIT_AUTHOR_NAME": "t",
            "GIT_AUTHOR_EMAIL": "t@example.invalid",
            "GIT_COMMITTER_NAME": "t",
            "GIT_COMMITTER_EMAIL": "t@example.invalid",
        }

        def git(*args: str) -> None:
            subprocess.run(
                ["git", "-c", "commit.gpgsign=false", "-C", str(repo), *args],
                check=True,
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )

        git("init", "-q")
        for rel, text in blobs.items():
            path = repo / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        git("add", "-A")
        git("commit", "-qm", "base")
        return repo

    def write_local(self, rel: str, text: str) -> Path:
        path = self.root / "apps/android" / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_derives_modified_port_mode_and_shims(self) -> None:
        repo = self.make_pristine(
            {
                "TMessagesProj/src/main/java/org/telegram/ui/Foo.java": "pin\n",
                "TMessagesProj/src/main/java/org/telegram/ui/Mine.java": "upstream mine\n",
            }
        )
        import subprocess

        pin = subprocess.check_output(
            ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True
        ).strip()
        overlay = json.loads(json.dumps(OVERLAY))
        overlay["upstream"]["pinned_commit"] = pin
        overlay["reference"]["commit"] = pin
        self.write_overlay(overlay)

        self.write_local(LOCAL, "pin\n")  # byte-clean vs the pin
        self.write_local("TMessagesProj/src/main/java/org/telegram/ui/Mine.java", "mine\n")
        self.write_local("TMessagesProj/src/main/java/org/telegram/ui/Renamed.java", "renamed\n")
        self.write_local("TMessagesProj/src/main/java/org/telegram/ui/Stub.java", "stub\n")
        self.write_local("app/src/main/java/example/Port.kt", "port\n")

        index = load_index(self.root)
        by_local = {e["local"]: e for e in index["files"]}
        shims = {e["local"]: e for e in index["compat_shims"]}

        self.assertFalse(by_local[LOCAL]["modified"])
        self.assertEqual("exact", by_local[LOCAL]["port_mode"])
        # overlay-native file that has an upstream counterpart
        self.assertEqual("native", by_local["TMessagesProj/src/main/java/org/telegram/ui/Mine.java"]["port_mode"])
        self.assertEqual(
            ["drawing", "measurement"],
            by_local["TMessagesProj/src/main/java/org/telegram/ui/Mine.java"]["forced_exact"],
        )
        # derived_from rewrites the upstream mapping
        self.assertEqual(
            "org/telegram/ui/Foo.java",
            by_local["TMessagesProj/src/main/java/org/telegram/ui/Renamed.java"]["upstream"],
        )
        # :app port keeps native + explicit upstream
        self.assertEqual("native", by_local["app/src/main/java/example/Port.kt"]["port_mode"])
        # file with no upstream counterpart becomes a shim, forced_exact survives
        stub = shims["TMessagesProj/src/main/java/org/telegram/ui/Stub.java"]
        self.assertEqual("shim", stub["origin"])
        self.assertEqual("native", stub["port_mode"])

    def test_reference_paths_use_their_own_baseline(self) -> None:
        repo = self.make_pristine({"TMessagesProj/src/main/java/org/telegram/ui/Foo.java": "pin\n"})
        import subprocess

        def rev(ref: str = "HEAD") -> str:
            return subprocess.check_output(
                ["git", "-C", str(repo), "rev-parse", ref], text=True
            ).strip()

        old = rev()
        # upstream moves on; the tracked file deliberately stays at `old`.
        (repo / "TMessagesProj/src/main/java/org/telegram/ui/Foo.java").write_text("new\n")
        subprocess.run(["git", "-c", "commit.gpgsign=false", "-C", str(repo), "commit", "-qam", "change"], check=True, env={
            **os.environ,
            "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@example.invalid",
            "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@example.invalid",
        }, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        pin = rev()

        overlay = json.loads(json.dumps(OVERLAY))
        overlay["upstream"]["pinned_commit"] = pin
        overlay["reference"] = {"commit": old, "why": "frozen", "paths": [LOCAL]}
        overlay["native"] = []
        overlay["upstream_ports"] = {}
        overlay["derived_from"] = {}
        overlay["forced_exact"] = {}
        self.write_overlay(overlay)
        self.write_local(LOCAL, "pin\n")  # identical to the reference blob

        index = load_index(self.root)
        entry = next(e for e in index["files"] if e["local"] == LOCAL)
        self.assertEqual(old, entry["vendored_at"])
        self.assertFalse(entry["modified"])

        # Without the reference block the same file diffs against the pin.
        overlay.pop("reference")
        self.write_overlay(overlay)
        index = load_index(self.root)
        entry = next(e for e in index["files"] if e["local"] == LOCAL)
        self.assertTrue(entry["modified"])


if __name__ == "__main__":
    unittest.main()
