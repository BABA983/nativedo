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
    default_vendor_dir,
    legacy_vendor_dir,
    vendor_dir,
)

MANIFEST = {"upstream": {"pinned_commit": "a" * 40, "repo": "git@example.invalid/t.git"}}


class VendorDirResolutionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "repo"
        self.root.mkdir()
        self.write_manifest(MANIFEST)
        patcher = mock.patch.dict(os.environ, {}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        os.environ.pop(VENDOR_DIR_ENV, None)

    def write_manifest(self, manifest: dict) -> None:
        (self.root / "vendor-manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_defaults_to_sibling_checkout_next_to_repo(self) -> None:
        self.assertEqual(default_vendor_dir(self.root), vendor_dir(self.root))
        self.assertEqual("telegram", vendor_dir(self.root).name)
        self.assertEqual(self.root.parent, vendor_dir(self.root).parent)

    def test_env_override_is_authoritative(self) -> None:
        target = Path(self.tmp.name) / "elsewhere" / "tg"
        os.environ[VENDOR_DIR_ENV] = str(target)
        # Even when the path does not exist yet (vendor-setup clones there).
        self.assertEqual(target, vendor_dir(self.root))

    def test_manifest_local_checkout_is_used_when_present(self) -> None:
        self.write_manifest(
            {**MANIFEST, "upstream": {**MANIFEST["upstream"], "local_checkout": "../pinned-tg"}}
        )
        checkout = self.root.parent / "pinned-tg"
        checkout.mkdir()
        self.assertEqual(checkout, vendor_dir(self.root))

    def test_upstream_repo_cased_sibling_still_resolves(self) -> None:
        # `git clone .../DrKLO/Telegram.git` produces `Telegram/`.
        (self.root.parent / "Telegram").mkdir()
        self.assertEqual(self.root.parent / "Telegram", vendor_dir(self.root))

    def test_legacy_in_repo_checkout_still_resolves(self) -> None:
        legacy = legacy_vendor_dir(self.root)
        legacy.mkdir(parents=True)
        self.assertEqual(legacy, vendor_dir(self.root))

    def test_sibling_wins_over_legacy_when_both_exist(self) -> None:
        sibling = default_vendor_dir(self.root)
        sibling.mkdir()
        legacy_vendor_dir(self.root).mkdir(parents=True)
        self.assertEqual(sibling, vendor_dir(self.root))

    def test_missing_manifest_falls_back_to_sibling(self) -> None:
        (self.root / "vendor-manifest.json").unlink()
        self.assertEqual(default_vendor_dir(self.root), vendor_dir(self.root))


if __name__ == "__main__":
    unittest.main()
