from __future__ import annotations

import argparse
import sys
from pathlib import Path


def try_fix_line(line: str) -> str | None:
    # Typical mojibake in this repo looks like Chinese text that was UTF-8 bytes
    # decoded as Latin-1 and saved back. Attempt latin1->utf8 roundtrip.
    if any("\u00c0" <= ch <= "\u00ff" for ch in line) and not any("\u4e00" <= ch <= "\u9fff" for ch in line):
        try:
            fixed = line.encode("latin-1").decode("utf-8")
        except UnicodeError:
            return None
        if any("\u4e00" <= ch <= "\u9fff" for ch in fixed):
            return fixed
    return None


def process_file(path: Path) -> tuple[bool, int]:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        text = path.read_text(encoding="utf-8", errors="replace")

    changed = False
    fixed_lines = 0
    out_lines: list[str] = []
    for line in text.splitlines(keepends=False):
        fixed = try_fix_line(line)
        if fixed is not None and fixed != line:
            out_lines.append(fixed)
            changed = True
            fixed_lines += 1
        else:
            out_lines.append(line)

    if not changed:
        return False, 0

    path.write_text("\n".join(out_lines) + ("\n" if text.endswith("\n") else ""), encoding="utf-8")
    return True, fixed_lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Only report, do not modify files")
    parser.add_argument("--apply", action="store_true", help="Apply fixes in-place")
    parser.add_argument(
        "--root",
        default="app/src/main/java",
        help="Root directory to scan",
    )
    args = parser.parse_args()

    if args.check == args.apply:
        print("Use exactly one of --check or --apply", file=sys.stderr)
        return 2

    root = Path(args.root)
    targets = [p for p in root.rglob("*.kt")]

    total_changed_files = 0
    total_fixed_lines = 0

    for path in targets:
        if args.check:
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                text = path.read_text(encoding="utf-8", errors="replace")
            for i, line in enumerate(text.splitlines(), start=1):
                fixed = try_fix_line(line)
                if fixed is not None and fixed != line:
                    print(f"{path.as_posix()}:{i}: {line.strip()}")
                    total_fixed_lines += 1
                    total_changed_files += 1
                    break
        else:
            changed, fixed_lines = process_file(path)
            if changed:
                total_changed_files += 1
                total_fixed_lines += fixed_lines

    if args.check:
        if total_fixed_lines == 0:
            print("OK: no mojibake candidates found.")
            return 0
        print(f"Found mojibake candidates in ~{total_changed_files} files.")
        return 1

    print(f"Fixed {total_fixed_lines} lines across {total_changed_files} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

