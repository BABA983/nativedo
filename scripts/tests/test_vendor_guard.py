from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

import vendor_guard  # noqa: E402


class ForcedExactTestMappingTest(unittest.TestCase):
    """The per-file prompt is data, so it is checked like data.

    A promoted file runs its mapped tests; anything else falls back to the
    shared trio. Both halves matter: a mapping that points at a renamed test, or
    at a file the overlay no longer forces, would send the next agent to a
    command that proves nothing.
    """

    @classmethod
    def setUpClass(cls) -> None:
        cls.overlay = json.loads((ROOT / "vendor-overlay.json").read_text(encoding="utf-8"))
        cls.forced = cls.overlay["forced_exact"]
        cls.tests = vendor_guard._test_classes(
            ROOT / "apps/android/app/src/test/java"
        )

    def test_mapped_files_are_forced_exact(self) -> None:
        stale = sorted(set(vendor_guard.FORCED_EXACT_TESTS_BY_PATH) - set(self.forced))
        self.assertEqual(
            [],
            stale,
            "FORCED_EXACT_TESTS_BY_PATH names files the overlay does not force "
            "(drop the entry, or force the file):\n  " + "\n  ".join(stale),
        )

    def test_mapped_tests_exist(self) -> None:
        missing = sorted(
            f"{local} -> {name}"
            for local, names in vendor_guard.FORCED_EXACT_TESTS_BY_PATH.items()
            for name in names
            if name not in self.tests
        )
        self.assertEqual(
            [],
            missing,
            "FORCED_EXACT_TESTS_BY_PATH names tests that are not in the test "
            "tree (rename the test or the entry):\n  " + "\n  ".join(missing),
        )

    def test_mapped_file_runs_only_its_tests(self) -> None:
        local = "TMessagesProj/src/main/java/org/telegram/ui/Components/LayoutHelper.java"
        tests, unknown = vendor_guard._forced_exact_tests(local, self.tests)
        self.assertEqual([], unknown)
        self.assertEqual(
            ["org.telegram.ui.Components.LayoutHelperForcedExactTest"], tests
        )

    def test_unmapped_file_falls_back_to_the_trio(self) -> None:
        tests, unknown = vendor_guard._forced_exact_tests(
            "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BottomSheet.java",
            self.tests,
        )
        self.assertEqual([], unknown)
        self.assertEqual(vendor_guard.DEFAULT_FORCED_EXACT_TESTS, tests)

    def test_unknown_mapped_name_is_reported(self) -> None:
        vendor_guard.FORCED_EXACT_TESTS_BY_PATH["some/File.java"] = ["NoSuchTest"]
        try:
            tests, unknown = vendor_guard._forced_exact_tests("some/File.java", self.tests)
        finally:
            del vendor_guard.FORCED_EXACT_TESTS_BY_PATH["some/File.java"]
        self.assertEqual(["NoSuchTest"], unknown)
        self.assertEqual([], tests)

    def test_candidates_match_by_class_name(self) -> None:
        candidates = vendor_guard._test_candidates(
            "TMessagesProj/src/main/java/org/telegram/ui/Cells/TextClock.java",
            {"TextClockContractTest": "org.telegram.ui.Cells.TextClockContractTest"},
        )
        self.assertEqual(["TextClockContractTest"], candidates)


if __name__ == "__main__":
    unittest.main()
