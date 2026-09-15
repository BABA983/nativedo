from __future__ import annotations

import re
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import vendorlib  # noqa: E402

# `from vendorlib import a, b` and the parenthesized multi-line form used inside
# the shell wrappers' `python3 -c` snippets.
IMPORT_RE = re.compile(
    r"from\s+vendorlib\s+import\s+(?:\((?P<multi>[^)]*)\)|(?P<single>[^\n\\#;]+))",
    re.S,
)


def script_files() -> list[Path]:
    return sorted(
        p
        for p in SCRIPTS.iterdir()
        if p.is_file() and p.suffix in (".py", ".sh")
    )


class VendorScriptHealthTest(unittest.TestCase):
    """Guards the tooling against rot after a model change.

    `scripts/auto-vendor-copy.sh` once kept importing `load_manifest` after the
    overlay rework removed it, and nothing noticed until someone ran it. Every
    `from vendorlib import ...` in the tree is checked against the module here,
    and standalone Python tools are checked for syntax errors.
    """

    def test_vendorlib_imports_resolve(self) -> None:
        available = set(dir(vendorlib))
        missing: list[str] = []
        for path in script_files():
            text = path.read_text(encoding="utf-8")
            for match in IMPORT_RE.finditer(text):
                names = match.group("multi") or match.group("single") or ""
                for raw in names.split(","):
                    name = raw.split("#")[0].split(";")[0].strip().split(" as ")[0].strip()
                    if not name or name == "*":
                        continue
                    if name not in available:
                        missing.append(f"{path.name}: vendorlib.{name}")
        self.assertEqual(
            [],
            missing,
            "scripts import names that vendorlib no longer defines "
            "(update the script to the current overlay model):\n  "
            + "\n  ".join(missing),
        )

    def test_python_tools_compile(self) -> None:
        broken: list[str] = []
        for path in script_files():
            if path.suffix != ".py":
                continue
            try:
                compile(path.read_text(encoding="utf-8"), str(path), "exec")
            except SyntaxError as e:
                broken.append(f"{path.name}: {e}")
        self.assertEqual([], broken, "scripts/*.py with syntax errors:\n  " + "\n  ".join(broken))

    def test_shell_scripts_parse(self) -> None:
        broken: list[str] = []
        for path in script_files():
            if path.suffix != ".sh":
                continue
            proc = subprocess.run(
                ["bash", "-n", str(path)], capture_output=True, text=True
            )
            if proc.returncode != 0:
                broken.append(f"{path.name}: {proc.stderr.strip()}")
        self.assertEqual([], broken, "scripts/*.sh with syntax errors:\n  " + "\n  ".join(broken))


if __name__ == "__main__":
    unittest.main()
