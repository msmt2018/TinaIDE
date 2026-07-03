from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


DEFAULT_HOST_RES_ROOTS = (
    "app/src/main/res",
    "core/*/src/main/res",
    "feature/*/src/main/res",
)

DEFAULT_EMBEDDED_RES_ROOTS = (
    "external/rikkahub/*/src/main/res",
)

ALLOWED_COLLISIONS = {
    ("mipmap", "ic_launcher"),
    ("mipmap", "ic_launcher_foreground"),
    ("xml", "backup_rules"),
    ("xml", "data_extraction_rules"),
    ("xml", "file_paths"),
}


ResourceOrigins = dict[tuple[str, str], set[str]]


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Check host TinaIDE resources against embedded RikkaHub resources. "
            "Unexpected shared names can make Android resource merging replace "
            "host strings such as the launcher label."
        )
    )
    parser.add_argument(
        "--host-res-root",
        action="append",
        dest="host_res_roots",
        help="Host Android res root glob. Can be passed multiple times.",
    )
    parser.add_argument(
        "--embedded-res-root",
        action="append",
        dest="embedded_res_roots",
        help="Embedded dependency Android res root glob. Can be passed multiple times.",
    )
    return parser.parse_args(argv)


def find_res_roots(root: Path, patterns: list[str]) -> list[Path]:
    directories: list[Path] = []
    for pattern in patterns:
        for path in root.glob(pattern):
            if path.is_dir() and "build" not in path.parts:
                directories.append(path)
    return sorted(set(directories))


def resource_type_from_directory(directory_name: str) -> str:
    return directory_name.split("-", maxsplit=1)[0]


def xml_local_name(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", maxsplit=1)[1]
    return tag


def values_resource_key(element: ET.Element) -> tuple[str, str] | None:
    name = element.attrib.get("name")
    if not name:
        return None

    tag = xml_local_name(element.tag)
    if tag == "item":
        resource_type = element.attrib.get("type")
        if not resource_type:
            return None
    elif tag == "declare-styleable":
        resource_type = "styleable"
    else:
        resource_type = tag

    return resource_type, name


def collect_values_resources(path: Path, repo_root: Path, origins: ResourceOrigins) -> None:
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        raise ValueError(f"{path}: invalid XML: {exc}") from exc

    root = tree.getroot()
    if xml_local_name(root.tag) != "resources":
        return

    for element in root:
        key = values_resource_key(element)
        if key is None:
            continue
        origins[key].add(path.relative_to(repo_root).as_posix())


def collect_file_resource(path: Path, repo_root: Path, origins: ResourceOrigins) -> None:
    resource_type = resource_type_from_directory(path.parent.name)
    if resource_type == "values":
        collect_values_resources(path, repo_root, origins)
        return

    origins[(resource_type, path.stem)].add(path.relative_to(repo_root).as_posix())


def collect_resources(repo_root: Path, res_roots: list[Path]) -> ResourceOrigins:
    origins: ResourceOrigins = defaultdict(set)
    for res_root in res_roots:
        for path in sorted(res_root.rglob("*")):
            if not path.is_file() or path.suffix != ".xml":
                continue
            collect_file_resource(path, repo_root, origins)
    return dict(origins)


def format_key(key: tuple[str, str]) -> str:
    resource_type, name = key
    return f"{resource_type}/{name}"


def print_origins(label: str, origins: set[str]) -> None:
    print(f"  {label}:")
    for origin in sorted(origins)[:10]:
        print(f"    - {origin}")
    if len(origins) > 10:
        print(f"    ... {len(origins) - 10} more")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

    args = parse_args(sys.argv[1:])
    root = Path.cwd()
    host_patterns = args.host_res_roots or list(DEFAULT_HOST_RES_ROOTS)
    embedded_patterns = args.embedded_res_roots or list(DEFAULT_EMBEDDED_RES_ROOTS)

    host_roots = find_res_roots(root, host_patterns)
    embedded_roots = find_res_roots(root, embedded_patterns)
    if not host_roots:
        print("FAILED: no host resource roots found.")
        return 1
    if not embedded_roots:
        print("FAILED: no embedded resource roots found.")
        return 1

    host_resources = collect_resources(root, host_roots)
    embedded_resources = collect_resources(root, embedded_roots)

    collisions = sorted((set(host_resources) & set(embedded_resources)) - ALLOWED_COLLISIONS)
    if not collisions:
        print(
            "OK: no unexpected host/embedded resource collisions "
            f"({len(ALLOWED_COLLISIONS)} allowed names)."
        )
        return 0

    print("Unexpected host/embedded Android resource collisions:")
    for key in collisions:
        print(f"- {format_key(key)}")
        print_origins("host", host_resources[key])
        print_origins("embedded", embedded_resources[key])

    print()
    print("Rename host-owned resources with a tina/tinaide prefix, or add a tightly justified allowlist entry.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
