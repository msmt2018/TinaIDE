from __future__ import annotations

import argparse
import datetime as _dt
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class FileScanResult:
    path: str
    module: str
    package: str | None
    group: str
    loc: int
    decls: list[str]


KOTLIN_DECL_RE = re.compile(
    r"(?m)^\s*"
    r"(?:(?:public|private|protected|internal)\s+)?"
    r"(?P<mods>(?:(?:expect|actual|final|open|abstract|sealed|data|enum|annotation|value|inline)\s+)*)"
    r"(?P<kind>class|interface|object)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b"
)
KOTLIN_TYPEALIAS_RE = re.compile(r"(?m)^\s*typealias\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b")
JAVA_DECL_RE = re.compile(
    r"(?m)^\s*"
    r"(?:(?:public|private|protected)\s+)?"
    r"(?:(?:static|final|abstract)\s+)*"
    r"(?P<kind>class|interface|enum|record)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b"
)
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+(?P<pkg>[a-zA-Z0-9_.]+)\s*")


def _loc(text: str) -> int:
    return len(text.splitlines())


def _group_for(package: str | None) -> str:
    if not package:
        return "(no package)"
    prefix = "com.wuxianggujun.tinaide."
    if package.startswith(prefix):
        rest = package[len(prefix) :]
        return prefix + rest.split(".", 1)[0]
    return package


def _format_kotlin_decl(mods: str, kind: str, name: str) -> str:
    mods = " ".join(
        [m for m in mods.strip().split() if m in {"sealed", "data", "enum", "annotation", "value"}]
    )
    if mods:
        return f"{mods} {kind} {name}"
    return f"{kind} {name}"


def _extract_decls(path: Path, text: str) -> list[str]:
    decls: list[str] = []
    if path.suffix == ".kt":
        for m in KOTLIN_DECL_RE.finditer(text):
            decls.append(_format_kotlin_decl(m.group("mods"), m.group("kind"), m.group("name")))
        for m in KOTLIN_TYPEALIAS_RE.finditer(text):
            decls.append(f"typealias {m.group('name')}")
        return decls

    for m in JAVA_DECL_RE.finditer(text):
        decls.append(f"{m.group('kind')} {m.group('name')}")
    return decls


def _scan_file(repo_root: Path, path: Path) -> FileScanResult:
    text = path.read_text(encoding="utf-8")
    package_match = PACKAGE_RE.search(text)
    package = package_match.group("pkg") if package_match else None
    rel_path = path.relative_to(repo_root).as_posix()
    module = "app" if "app/src/main" in rel_path else "language-cmake"
    return FileScanResult(
        path=rel_path,
        module=module,
        package=package,
        group=_group_for(package),
        loc=_loc(text),
        decls=_extract_decls(path, text),
    )


def build_index(repo_root: Path) -> list[FileScanResult]:
    targets = [
        repo_root / "app" / "src" / "main" / "java" / "com" / "wuxianggujun" / "tinaide",
        repo_root / "language-cmake" / "src" / "main" / "java",
    ]

    results: list[FileScanResult] = []
    for base in targets:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.is_file() and path.suffix in {".kt", ".java"}:
                results.append(_scan_file(repo_root, path))

    return sorted(results, key=lambda r: r.path)


def render_markdown(results: list[FileScanResult], generated_at: str) -> str:
    total_files = len(results)
    total_decls = sum(len(r.decls) for r in results)

    by_module: dict[str, dict[str, int]] = {}
    by_group: dict[str, dict[str, int]] = {}
    for r in results:
        by_module.setdefault(r.module, {"files": 0, "decls": 0, "loc": 0})
        by_module[r.module]["files"] += 1
        by_module[r.module]["decls"] += len(r.decls)
        by_module[r.module]["loc"] += r.loc

        by_group.setdefault(r.group, {"files": 0, "decls": 0})
        by_group[r.group]["files"] += 1
        by_group[r.group]["decls"] += len(r.decls)

    largest20 = sorted(results, key=lambda r: r.loc, reverse=True)[:20]

    lines: list[str] = []
    lines.append("# TinaIDE 类清单（自动生成）")
    lines.append("")
    lines.append(f"> 生成时间：{generated_at}")
    lines.append(
        "> 范围：`app/src/main/java/com/wuxianggujun/tinaide/**` + `language-cmake/src/main/java/**`（不含 `external/` 等第三方源码）"
    )
    lines.append("")
    lines.append("**统计**：")
    lines.append(f"- 扫描文件数：{total_files}")
    lines.append(
        f"- 识别到的类型声明数：{total_decls}（Kotlin: class/interface/object/typealias；Java: class/interface/enum/record）"
    )
    lines.append("")
    lines.append("| 模块 | 文件数 | 类型声明数 | 代码行数(LOC) |")
    lines.append("|---|---:|---:|---:|")
    for module in sorted(by_module.keys()):
        s = by_module[module]
        lines.append(f"| `{module}` | {s['files']} | {s['decls']} | {s['loc']} |")
    lines.append("")
    lines.append("**一级子包分布**：")
    lines.append("")
    lines.append("| 分组 | 文件数 | 类型声明数 |")
    lines.append("|---|---:|---:|")
    for group in sorted(by_group.keys()):
        s = by_group[group]
        lines.append(f"| `{group}` | {s['files']} | {s['decls']} |")
    lines.append("")
    lines.append("**大文件 Top 20（LOC）**：")
    lines.append("")
    lines.append("| 排名 | 文件 | LOC |")
    lines.append("|---:|---|---:|")
    for i, r in enumerate(largest20, 1):
        lines.append(f"| {i} | `{r.path}` | {r.loc} |")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 详细清单（按一级子包折叠）")
    lines.append("")

    by_group_files: dict[str, list[FileScanResult]] = {}
    for r in results:
        by_group_files.setdefault(r.group, []).append(r)

    for group in sorted(by_group_files.keys()):
        files = by_group_files[group]
        decls = sum(len(f.decls) for f in files)
        lines.append("<details>")
        lines.append(f"<summary><code>{group}</code>（文件 {len(files)}，声明 {decls}）</summary>")
        lines.append("")
        lines.append("| 文件 | 声明 |")
        lines.append("|---|---|")
        for f in files:
            if not f.decls:
                continue
            lines.append(f"| `{f.path}` | " + ", ".join(f.decls) + " |")
        lines.append("")
        lines.append("</details>")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate TinaIDE class/type index Markdown.")
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root (default: current directory).",
    )
    parser.add_argument(
        "--output",
        default="docs/类清单_自动生成.md",
        help="Output markdown file path (default: docs/类清单_自动生成.md).",
    )
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    output = (repo_root / args.output).resolve()

    results = build_index(repo_root)
    generated_at = _dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    content = render_markdown(results, generated_at)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
