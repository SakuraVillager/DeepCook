#!/usr/bin/env python3
"""Build the bundled name index for the online recipe lookup.

The app lets the user type a dish name; if the device is online it fetches the
matching HowToCook (https://github.com/Anduin2017/HowToCook, Unlicense) page and
prefills the form. Two things are needed for that:

* a name -> path index, so matching works instantly and also while offline;
* a pinned commit, so a lookup always returns the text this project reviewed.

Both are produced here and written to ``assets/howtocook_index.json``. The app
never lists the repository itself, so it makes exactly one request per lookup.

Usage::

    python tools/build_howtocook_index.py            # pin to current master
    python tools/build_howtocook_index.py --ref <sha>  # pin to a specific commit

The file is only regenerated when the upstream catalog should be refreshed;
review the diff before committing it.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request

REPO = "Anduin2017/HowToCook"
LICENSE = "Unlicense"
INDEX_NAME = "howtocook_index.json"
# Dishes live under dishes/<group>/...; the template folder is a scaffold, not a dish.
EXCLUDED_PREFIXES = ("dishes/template/",)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSET = os.path.join(HERE, os.pardir, "app", "src", "main", "assets", INDEX_NAME)


def api(path: str) -> dict:
    request = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/{path}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "DeepCook-index-builder"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def resolve_ref() -> tuple[str, str]:
    """Return (commit sha, commit ISO date) for the tip of the default branch."""
    commit = api("commits/master")
    return commit["sha"], commit["commit"]["committer"]["date"]


def dish_paths(sha: str) -> list[str]:
    tree = api(f"git/trees/{sha}?recursive=1")
    if tree.get("truncated"):
        raise SystemExit("The upstream tree was truncated; refusing to write a partial index.")
    paths = [
        entry["path"]
        for entry in tree["tree"]
        if entry["type"] == "blob"
        and entry["path"].startswith("dishes/")
        and entry["path"].endswith(".md")
        and not entry["path"].startswith(EXCLUDED_PREFIXES)
    ]
    return sorted(paths)


def build(sha: str, date: str, paths: list[str]) -> dict:
    dishes = []
    for path in paths:
        # dishes/<group>/<file>.md or dishes/<group>/<dish>/<file>.md
        parts = path.split("/")
        name = parts[-1][: -len(".md")]
        dishes.append({"name": name, "path": path, "group": parts[1]})
    names = [dish["name"] for dish in dishes]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    return {
        "source": REPO,
        "license": LICENSE,
        "ref": sha,
        "commitDate": date,
        "duplicateNames": duplicates,
        "dishes": dishes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", help="commit sha to pin; defaults to the current master")
    parser.add_argument("--out", default=ASSET, help="output path for the index")
    arguments = parser.parse_args()

    if arguments.ref:
        sha, date = arguments.ref, api(f"commits/{arguments.ref}")["commit"]["committer"]["date"]
    else:
        sha, date = resolve_ref()

    paths = dish_paths(sha)
    index = build(sha, date, paths)

    with open(arguments.out, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(index, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")

    print(f"ref      {sha}")
    print(f"date     {date}")
    print(f"dishes   {len(index['dishes'])}")
    print(f"dupes    {len(index['duplicateNames'])} {index['duplicateNames'][:5]}")
    print(f"size     {os.path.getsize(arguments.out)} bytes")
    print(f"written  {os.path.abspath(arguments.out)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
