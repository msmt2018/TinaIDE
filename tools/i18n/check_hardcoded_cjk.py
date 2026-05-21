from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


KOTLIN_STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def mask_block_comments(text: str) -> str:
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        if i + 1 < n and text[i] == "/" and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            if end == -1:
                end = n - 2
            block = text[i : end + 2]
            out.append("".join("\n" if ch == "\n" else " " for ch in block))
            i = end + 2
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def strip_line_comments(line: str) -> str:
    # Best-effort: avoid counting // comments. Doesn't parse strings; good enough for CI hints.
    return line.split("//", 1)[0]


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        default="app/src/main/java",
        help="Kotlin source root to scan",
    )
    parser.add_argument(
        "--include-logs",
        action="store_true",
        help="Also scan Timber/Log lines",
    )
    parser.add_argument(
        "--max-hits",
        type=int,
        default=200,
        help="Max number of hits to print",
    )
    return parser.parse_args(argv)


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

    args = parse_args(sys.argv[1:])
    root = Path(args.root)
    hits: list[tuple[str, int, str]] = []

    for path in root.rglob("*.kt"):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            text = path.read_text(encoding="utf-8", errors="replace")

        masked = mask_block_comments(text)

        for i, raw_line in enumerate(masked.splitlines(), start=1):
            line = strip_line_comments(raw_line)
            if (
                not args.include_logs
                and ("Timber." in line or "android.util.Log." in line or "Log." in line)
            ):
                continue

            for m in KOTLIN_STRING_RE.finditer(line):
                s = m.group(0)
                if CJK_RE.search(s):
                    hits.append((path.as_posix(), i, s[:120]))

    if not hits:
        print("OK: no hardcoded CJK strings found in Kotlin string literals.")
        return 0

    print(f"Found {len(hits)} hardcoded CJK string literals:")
    for p, line_no, preview in hits[: args.max_hits]:
        print(f"{p}:{line_no}: {preview}")
    if len(hits) > args.max_hits:
        print(f"... truncated, total={len(hits)}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
