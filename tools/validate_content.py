"""Validate the bundled recipe catalog without an Android build.

Run from the repository root:  python tools/validate_content.py

Checks mirror what the app requires at runtime (Recipe.init, AssetRecipeRepository)
plus the content conventions documented in README.md and docs/菜谱扩展1.md.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"

REGIONS = ["中式", "日式", "韩式", "美式", "欧式", "东南亚", "其他"]
CATEGORIES = [
    "家常菜", "快手菜", "素食", "肉类", "海鲜", "汤羹",
    "主食", "面食", "烘焙甜点", "早餐", "沙拉",
]
NO = "无"
EXPECTED_REGIONS = {"中式": 52, "日式": 15, "韩式": 12, "美式": 10, "欧式": 10, "东南亚": 8, "其他": 5}
SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
FEATURED = 6
STEPS_RANGE = (3, 6)
MINUTES_RANGE = (1, 1440)
INGREDIENTS_RANGE = (3, 14)

errors: list[str] = []


def check(condition, message):
    if not condition:
        errors.append(message)


def main():
    raw = (ASSETS / "recipes.json").read_text(encoding="utf-8")
    recipes = json.loads(raw)

    check(isinstance(recipes, list) and recipes, "recipes.json must be a non-empty array")
    ids = [r.get("id") for r in recipes]
    titles = [r.get("title") for r in recipes]
    check(len(set(ids)) == len(ids), f"duplicate recipe ids: {[i for i, n in Counter(ids).items() if n > 1]}")
    check(len(set(titles)) == len(titles), f"duplicate titles: {[t for t, n in Counter(titles).items() if n > 1]}")

    photos = {p.name for p in (ASSETS / "photos").iterdir() if p.is_file()}
    featured = 0
    for recipe in recipes:
        rid = recipe.get("id", "<missing id>")
        where = f"[{rid}]"
        check(bool(SLUG.match(rid)), f"{where} id must be an ASCII slug")
        check(recipe.get("version") == 3, f"{where} version must be 3")
        check(recipe.get("region") in REGIONS, f"{where} region must be one of {REGIONS}")
        check(bool(recipe.get("title")), f"{where} title is required")
        check(len(recipe.get("description", "")) >= 10, f"{where} description is too short")
        check(bool(recipe.get("servings", "").startswith("原始份量")), f"{where} servings must start with 原始份量")

        categories = recipe.get("categories") or []
        check(bool(categories), f"{where} needs at least one category")
        for category in categories:
            check(category in CATEGORIES, f"{where} unknown category {category!r}")

        minutes = recipe.get("minutes")
        # Missing metadata stays genuinely empty (the UI shows "—"), so null is allowed
        # while any provided value must be a usable cooking time.
        check(
            minutes is None or (isinstance(minutes, int) and MINUTES_RANGE[0] <= minutes <= MINUTES_RANGE[1]),
            f"{where} minutes must be empty or {MINUTES_RANGE[0]}–{MINUTES_RANGE[1]}",
        )
        difficulty = recipe.get("difficulty")
        check(difficulty is None or 1 <= difficulty <= 5, f"{where} difficulty must be 1–5")

        cover = recipe.get("cover")
        if cover is not None:
            check(cover.startswith("photos/") and cover.split("/", 1)[1] in photos, f"{where} cover missing in assets")

        ingredients = recipe.get("ingredients") or []
        check(
            INGREDIENTS_RANGE[0] <= len(ingredients) <= INGREDIENTS_RANGE[1],
            f"{where} ingredients must be {INGREDIENTS_RANGE[0]}–{INGREDIENTS_RANGE[1]}",
        )
        ingredient_ids = [i.get("id") for i in ingredients]
        check(len(set(ingredient_ids)) == len(ingredient_ids), f"{where} duplicate ingredient ids")
        check(ingredient_ids == [f"i{n}" for n in range(len(ingredient_ids))], f"{where} ingredient ids must be i0..in")
        for ingredient in ingredients:
            check(bool(ingredient.get("name")), f"{where} ingredient name is empty")
            check(bool(ingredient.get("amount")), f"{where} ingredient {ingredient.get('id')} has no amount")

        preparation = recipe.get("preparation")
        check(isinstance(preparation, list) and preparation, f"{where} preparation must be [\"{NO}\"] when empty")
        check(all(str(p).strip() for p in preparation), f"{where} preparation has a blank entry")
        notes = recipe.get("notes")
        check(isinstance(notes, list) and notes, f"{where} notes must be [\"{NO}\"] when empty")
        check(all(str(n).strip() for n in notes), f"{where} notes has a blank entry")

        steps = recipe.get("steps") or []
        check(
            STEPS_RANGE[0] <= len(steps) <= STEPS_RANGE[1],
            f"{where} steps must be {STEPS_RANGE[0]}–{STEPS_RANGE[1]}",
        )
        step_ids = [s.get("id") for s in steps]
        check(len(set(step_ids)) == len(step_ids), f"{where} duplicate step ids")
        for index, step in enumerate(steps):
            check(step.get("id") == f"{rid}-s{index + 1}", f"{where} step {index + 1} id must be {rid}-s{index + 1}")
            check(step.get("order") == index + 1, f"{where} step {index + 1} order must be {index + 1}")
            check(bool(step.get("title")), f"{where} step {index + 1} title is empty")
            check(bool(step.get("text")), f"{where} step {index + 1} text is empty")
            cue = step.get("cue")
            check(isinstance(cue, str) and cue.strip() != "", f"{where} step {index + 1} cue must be text (use \"{NO}\")")
            refs = step.get("ingredientRefs") or []
            unknown = [ref for ref in refs if ref not in ingredient_ids]
            check(not unknown, f"{where} step {index + 1} references unknown ingredients {unknown}")

        source = recipe.get("source") or {}
        check(bool(source.get("author")), f"{where} source.author is required")
        check(bool(source.get("license")), f"{where} source.license is required")
        url = source.get("url")
        check(url is None or url.startswith("https://"), f"{where} source.url must be https")

        if recipe.get("featured"):
            featured += 1

    check(featured == FEATURED, f"featured recipes must be {FEATURED}, found {featured}")

    regions = Counter(r.get("region") for r in recipes)
    check(dict(regions) == EXPECTED_REGIONS, f"region counts {dict(regions)} != {EXPECTED_REGIONS}")

    seabass = [r["id"] for r in recipes if "鲈鱼" in json.dumps(r, ensure_ascii=False)]
    check(len(seabass) == 1, f"exactly one 鲈鱼 recipe is expected (UI test), found {seabass}")

    # Every bundled cover must be attributed in photo_sources.json, and no manifest
    # entry may point at a file that is not actually shipped.
    manifest_path = ASSETS / "photo_sources.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    credited = {entry["bundled"] for entry in manifest["images"]}
    credited |= {entry["bundled"] for entry in manifest.get("cc0Expansion", {}).get("images", [])}
    for recipe in recipes:
        cover = recipe.get("cover")
        if cover is not None:
            check(cover in credited, f"[{recipe['id']}] cover {cover} is missing from photo_sources.json")
    shipped = {f"photos/{path.name}" for path in (ASSETS / "photos").iterdir()}
    for entry in credited:
        check(entry in shipped, f"photo_sources.json lists {entry} but the file is not bundled")
        check(any(r.get("cover") == entry for r in recipes), f"photo_sources.json lists unused image {entry}")

    if errors:
        print(f"{len(errors)} content problem(s):")
        for message in errors:
            print("  -", message)
        return 1

    print(f"OK: {len(recipes)} recipes, featured {featured}, regions {dict(regions)}")
    print("steps:", Counter(len(r["steps"]) for r in recipes).most_common())
    print(
        "difficulty:",
        dict(sorted(Counter(r["difficulty"] for r in recipes).items(), key=lambda kv: (kv[0] is None, kv[0]))),
    )
    timed = [r["minutes"] for r in recipes if r["minutes"] is not None]
    print("minutes: min", min(timed), "max", max(timed), "unset", len(recipes) - len(timed))
    print("covers:", sum(1 for r in recipes if r["cover"]), "with image")
    return 0


if __name__ == "__main__":
    sys.exit(main())
