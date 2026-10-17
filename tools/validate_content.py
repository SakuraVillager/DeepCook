"""Validate content invariants, provenance and offline assets without fixed regional quotas."""
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
REGIONS = {'中式', '日式', '韩式', '美式', '欧式', '东南亚', '其他', '无'}


def main():
    errors = []
    def check(ok, message):
        if not ok:
            errors.append(message)
    def asset(path):
        target = (ASSETS / path).resolve()
        return target.is_relative_to(ASSETS.resolve()) and target.is_file()
    recipes = json.loads((ASSETS / 'recipes.json').read_text(encoding='utf-8'))
    check(bool(recipes), 'Catalog is empty')
    ids = [r['id'] for r in recipes]
    check(len(ids) == len(set(ids)), 'Duplicate recipe IDs')
    # Titles may repeat across independently attributed variants. IDs must not repeat.
    for r in recipes:
        rid = r['id']
        check(bool(re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', rid)), f'{rid}: invalid ID')
        check(r.get('version', 0) > 0, f'{rid}: invalid version')
        check(bool(r.get('title', '').strip()), f'{rid}: blank title')
        check(r.get('region', '无') in REGIONS, f'{rid}: unknown region')
        check(bool(r.get('categories')) and all(str(c).strip() for c in r['categories']), f'{rid}: categories missing')
        check(bool(r.get('servings', '').strip()), f'{rid}: servings must use 无 when unknown')
        check(r.get('minutes') is None or (type(r['minutes']) is int and r['minutes'] > 0), f'{rid}: invalid time')
        check(r.get('difficulty') is None or (type(r['difficulty']) is int and 1 <= r['difficulty'] <= 5), f'{rid}: invalid difficulty')
        if r.get('cover'):
            check(asset(r['cover']), f'{rid}: missing cover {r["cover"]}')
        ingredients = r.get('ingredients', [])
        check(bool(ingredients), f'{rid}: missing ingredients')
        ingredient_ids = [i['id'] for i in ingredients]
        check(len(set(ingredient_ids)) == len(ingredient_ids), f'{rid}: duplicate ingredient IDs')
        for i in ingredients:
            check(bool(i.get('name', '').strip()) and bool(i.get('amount', '').strip()), f'{rid}: invalid ingredient')
        steps = r.get('steps', [])
        check(bool(steps), f'{rid}: missing steps')
        check(len({s['id'] for s in steps}) == len(steps), f'{rid}: duplicate step IDs')
        for index, step in enumerate(steps, 1):
            check(step['order'] == index and bool(step.get('text', '').strip()), f'{rid}: invalid step {index}')
            check(set(step.get('ingredientRefs', [])) <= set(ingredient_ids), f'{rid}: dangling ingredient reference')
            if step.get('image'):
                check(asset(step['image']), f'{rid}: step image missing')
        for name in ('preparation', 'notes'):
            check(isinstance(r.get(name, []), list), f'{rid}: invalid {name}')
        source = r.get('source', {})
        check(bool(source.get('author')) and bool(source.get('license')), f'{rid}: missing attribution')
        check(source.get('url') is None or source['url'].startswith('https://'), f'{rid}: invalid source URL')
    check(sum(bool(r.get('featured')) for r in recipes) == 6, 'Home requires six featured recipes')

    manifest = json.loads((ASSETS / 'catalog_sources.json').read_text(encoding='utf-8'))
    entries = {e['recipeId']: e for e in manifest['recipes']}
    check(set(entries) == set(ids), 'Provenance must cover every recipe')
    check(len(entries) == len(manifest['recipes']), 'Duplicate provenance IDs')
    for r in recipes:
        entry = entries.get(r['id'], {})
        check(bool(re.fullmatch('[0-9a-f]{40}', entry.get('commit', ''))), f'{r["id"]}: source not pinned')
        check(bool(re.fullmatch('[0-9a-f]{64}', entry.get('originalSha256', ''))), f'{r["id"]}: checksum missing')
        check(entry.get('bundled') == r.get('cover'), f'{r["id"]}: photo attribution mismatch')
    index = json.loads((ASSETS / 'howtocook_index.json').read_text(encoding='utf-8'))
    check(index['source'] == 'Anduin2017/HowToCook', 'Invalid lookup repository')
    check(bool(re.fullmatch('[0-9a-f]{40}', index['ref'])), 'Lookup source must be pinned')
    dishes = index['dishes']
    check(bool(dishes) and len({d['name'] for d in dishes}) == len(dishes), 'Invalid lookup names')
    for dish in dishes:
        check(dish['path'].startswith('dishes/') and dish['path'].endswith('.md') and '..' not in dish['path'].split('/'), 'Invalid lookup path')
    if errors:
        print('\n'.join(errors))
        return 1
    print(f'OK: {len(recipes)} recipes, {sum(bool(r.get("cover")) for r in recipes)} covers, {len(dishes)} lookup names')
    print('Collections:', dict(Counter(r.get('collection') for r in recipes)))
    print('Regions:', dict(Counter(r.get('region', '无') for r in recipes)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
