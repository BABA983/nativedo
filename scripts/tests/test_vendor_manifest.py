from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from vendorlib import (  # noqa: E402
    CURRENT_PIN,
    LEGACY_PIN,
    ManifestError,
    SOURCE_DIGEST_KEY,
    compact_manifest,
    expand_source_manifest,
    load_manifest,
    manifest_lock_path,
    manifest_path,
    save_manifest,
)


def sample_flat_manifest() -> dict:
    return {
        "upstream": {
            "repo": "git@example.invalid/upstream.git",
            "pinned_commit": CURRENT_PIN,
            "pinned_date": "2026-06-16",
        },
        "local_root": "apps/android/",
        "upstream_root": "TMessagesProj/src/main/java",
        "files": [
            {
                "_comment": "core",
                "local": "telegram/src/main/java/org/telegram/Foo.java",
                "upstream": "org/telegram/Foo.java",
                "vendored_at": CURRENT_PIN,
                "modified": False,
                "port_mode": "exact",
                "notes": ["copied", "verbatim"],
            },
            {
                "local": "app/src/main/java/example/NativePort.java",
                "upstream": "org/telegram/NativePort.java",
                "vendored_at": LEGACY_PIN,
                "modified": True,
                "port_mode": "native",
                "forced_exact": ["drawing"],
                "stuck_reason": "reference only",
            },
            {
                "local": "telegram/src/main/java/org/telegram/CompileShim.java",
                "upstream": "org/telegram/CompileShim.java",
                "vendored_at": LEGACY_PIN,
                "modified": True,
                "port_mode": "native",
                "notes": "scratch compile shim",
                "stuck_reason": "reference only",
            },
        ],
        "resources": [],
    }


class VendorManifestTest(unittest.TestCase):
    def test_compact_expand_round_trip_preserves_provenance(self) -> None:
        shim = "telegram/src/main/java/org/telegram/CompileShim.java"
        source = compact_manifest(
            sample_flat_manifest(),
            compat_shim_locals={shim},
        )
        expanded = expand_source_manifest(source)

        self.assertEqual(2, len(expanded["files"]))
        self.assertEqual(1, len(expanded["compat_shims"]))
        self.assertEqual("copied verbatim", expanded["files"][0]["notes"])
        self.assertEqual(LEGACY_PIN, expanded["files"][1]["vendored_at"])
        self.assertEqual(["drawing"], expanded["files"][1]["forced_exact"])
        self.assertEqual("reference only", expanded["files"][1]["stuck_reason"])
        self.assertNotIn("stuck_reason", expanded["compat_shims"][0])

    def test_load_rejects_stale_lock_and_save_repairs_both_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = compact_manifest(sample_flat_manifest())
            manifest_path(root).write_text(
                json.dumps(source, indent=2) + "\n",
                encoding="utf-8",
            )
            manifest_lock_path(root).write_text("{}\n", encoding="utf-8")

            with self.assertRaises(ManifestError):
                load_manifest(root)

            expanded = expand_source_manifest(source)
            save_manifest(expanded, root)
            loaded = load_manifest(root)
            loaded.pop(SOURCE_DIGEST_KEY)
            self.assertEqual(expanded, loaded)

    def test_save_preserves_compat_shim_classification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shim = "telegram/src/main/java/org/telegram/CompileShim.java"
            source = compact_manifest(
                sample_flat_manifest(),
                compat_shim_locals={shim},
            )
            expanded = expand_source_manifest(source)
            save_manifest(expanded, root)

            loaded = load_manifest(root)
            self.assertEqual([shim], [entry["local"] for entry in loaded["compat_shims"]])
            self.assertNotIn(shim, [entry["local"] for entry in loaded["files"]])

    def test_duplicate_local_across_file_and_shim_is_rejected(self) -> None:
        flat = sample_flat_manifest()
        duplicate = dict(flat["files"][0])
        flat["compat_shims"] = [duplicate]
        source = compact_manifest(flat)
        with self.assertRaises(ManifestError):
            expand_source_manifest(source)

    def test_unknown_compact_field_is_rejected(self) -> None:
        source = compact_manifest(sample_flat_manifest())
        source["file_groups"][0]["entries"][0]["modifed"] = True
        with self.assertRaises(ManifestError):
            expand_source_manifest(source)

    def test_resource_must_be_an_object_with_kind_and_pin(self) -> None:
        source = compact_manifest(sample_flat_manifest())
        source["resources"] = ["bad"]
        with self.assertRaises(ManifestError):
            expand_source_manifest(source)

    def test_save_rejects_concurrent_source_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expanded = expand_source_manifest(compact_manifest(sample_flat_manifest()))
            save_manifest(expanded, root)
            first = load_manifest(root)
            second = load_manifest(root)

            second["upstream"]["pinned_date"] = "2026-06-17"
            save_manifest(second, root)
            with self.assertRaises(ManifestError):
                save_manifest(first, root)


if __name__ == "__main__":
    unittest.main()
