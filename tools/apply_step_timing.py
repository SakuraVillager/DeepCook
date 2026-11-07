"""Write a step-timing proposal into recipes.json (phase 2 of the step-timing work).

The proposal comes from `tools/propose_step_timing.py`. Every value is validated again here,
and only `minutes` / `kind` are written onto steps whose ids already exist. The script never
adds or removes a step or another field, and running it twice changes nothing.

Default is a dry run; pass `--apply` to write. Afterwards run `tools/validate_content.py`.

Usage:
    python tools/apply_step_timing.py --proposal tools/step_timing.json
    python tools/apply_step_timing.py --proposal tools/step_timing.json --apply
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RECIPES = ROOT / 'app/src/main/assets/recipes.json'
DEFAULT_PROPOSAL = ROOT / 'tools/step_timing.json'
STEP_KINDS = {'ACTIVE', 'PASSIVE'}


def merge(recipes, items):
    """Mutates [recipes] in place; returns a summary. Invalid values are skipped, not repaired."""
    known = {}
    for recipe in recipes:
        for step in recipe.get('steps', []):
            known[step['id']] = step
    summary = {
        'proposal': len(items),
        'known': len(known),
        'applied': 0,
        'changed': 0,
        'unknown': [],
        'invalid': [],
        'missing': 0,
    }
    seen = set()
    for step_id, item in items.items():
        step = known.get(step_id)
        if step is None:
            summary['unknown'].append(step_id)
            continue
        seen.add(step_id)
        minutes = item.get('minutes')
        kind = item.get('kind')
        if minutes is not None and (
            isinstance(minutes, bool) or not isinstance(minutes, int) or not 1 <= minutes <= 180
        ):
            summary['invalid'].append((step_id, 'minutes', minutes))
            continue
        if kind is not None and kind not in STEP_KINDS:
            summary['invalid'].append((step_id, 'kind', kind))
            continue
        if minutes is None and kind is None:
            summary['invalid'].append((step_id, 'empty', None))
            continue
        before = (step.get('minutes'), step.get('kind'))
        if minutes is not None:
            step['minutes'] = minutes
        if kind is not None:
            step['kind'] = kind
        summary['applied'] += 1
        if (step.get('minutes'), step.get('kind')) != before:
            summary['changed'] += 1
    summary['missing'] = sum(1 for step_id in known if step_id not in seen)
    summary['classified'] = sum(1 for step in known.values() if step.get('kind') is not None)
    summary['timed'] = sum(1 for step in known.values() if step.get('minutes') is not None)
    return summary


def write(recipes, path):
    text = json.dumps(recipes, ensure_ascii=False, indent=2) + '\n'
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(text, encoding='utf-8')
    temp.replace(path)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Merge a step-timing proposal into recipes.json.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('--proposal', type=Path, default=DEFAULT_PROPOSAL)
    parser.add_argument('--recipes', type=Path, default=DEFAULT_RECIPES)
    parser.add_argument('--apply', action='store_true', help='write the file (default is a dry run)')
    parser.add_argument('--require-complete', action='store_true', help='refuse to apply while steps are missing or invalid')
    args = parser.parse_args(argv)

    proposal = json.loads(args.proposal.read_text(encoding='utf-8'))
    items = proposal.get('items', {})
    recipes = json.loads(args.recipes.read_text(encoding='utf-8'))
    summary = merge(recipes, items)

    print(f"提案 {summary['proposal']} 条，菜谱 {summary['known']} 步")
    print(f"应用 {summary['applied']} 条（实际改动 {summary['changed']}），没有提案的步骤 {summary['missing']}")
    print(f"未知 id {len(summary['unknown'])}，非法值 {len(summary['invalid'])}")
    for step_id, field, value in summary['invalid'][:10]:
        print(f'  跳过 {step_id}：{field}={value!r}')
    for step_id in summary['unknown'][:10]:
        print(f'  未知 id：{step_id}')
    print(
        f"合并后覆盖：{summary['classified']}/{summary['known']} 步有类型，"
        f"{summary['timed']}/{summary['known']} 步有时长",
    )
    if summary['missing']:
        print('提示：还有步骤没有提案，可重跑 propose_step_timing.py 补齐。')

    blocked = args.require_complete and (summary['missing'] or summary['unknown'] or summary['invalid'])
    if not args.apply:
        print('dry run：未写入 recipes.json（加 --apply 才会写）。')
        return 1 if args.require_complete and blocked else 0
    if blocked:
        print('拒绝写入：--require-complete 要求提案完整且全部合法。')
        return 1
    write(recipes, args.recipes)
    print(f'已写入 {args.recipes}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
