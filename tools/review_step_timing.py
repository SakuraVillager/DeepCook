"""Review a step-timing proposal before it is merged into recipes.json.

Read-only: reads the proposal and the catalogue, prints coverage and sanity checks, and writes
a markdown report. It decides nothing on its own; it points a human at the rows worth reading.

Usage:
    python tools/review_step_timing.py
    python tools/review_step_timing.py --proposal tools/step_timing.json --out tools/step_timing_review.md
"""
import argparse
import json
import statistics
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
DEFAULT_PROPOSAL = ROOT / 'tools/step_timing.json'
DEFAULT_OUT = ROOT / 'tools/step_timing_review.md'
# Mirrors tools/validate_content.py.
PASSIVE_HINTS = ('腌制', '腌渍', '炖', '焖', '蒸', '卤', '冷藏', '静置', '发酵', '晾凉', '冷却', '醒面', '浸泡')
# Words that usually mean the cook is doing something, even inside a mostly-passive step.
ACTION_HINTS = ('取出', '装盘', '盛盘', '搅拌', '撒', '淋', '翻面', '切片', '切块', '洗净', '倒入', '调匀', '拌匀')
BUCKETS = (1, 2, 5, 10, 20, 45, 90, 180)


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def bucket(minutes):
    for edge in BUCKETS:
        if minutes <= edge:
            return f'≤{edge}'
    return '>180'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--proposal', type=Path, default=DEFAULT_PROPOSAL)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)

    proposal = load(args.proposal)
    items = proposal.get('items', {})
    recipes = load(ASSETS / 'recipes.json')

    rows = []
    for recipe in recipes:
        for step in recipe.get('steps', []):
            item = items.get(step['id'])
            if item:
                rows.append((recipe, step, item))
    all_steps = sum(len(recipe.get('steps', [])) for recipe in recipes)

    kinds = Counter(item['kind'] for _, _, item in rows)
    minutes = [item['minutes'] for _, _, item in rows if item['minutes'] is not None]
    without_minutes = [(recipe, step, item) for recipe, step, item in rows if item['minutes'] is None]
    passive_without_minutes = [row for row in without_minutes if row[2]['kind'] == 'PASSIVE']
    active_with_hint = [
        row for row in rows
        if row[2]['kind'] == 'ACTIVE' and any(hint in row[1].get('text', '') + row[1].get('title', '') for hint in PASSIVE_HINTS)
    ]
    passive_with_action = [
        row for row in rows
        if row[2]['kind'] == 'PASSIVE' and any(word in row[1].get('text', '') + row[1].get('title', '') for word in ACTION_HINTS)
    ]

    # A recipe's stated total is a crude scale check for its step estimates.
    ratios = []
    for recipe in recipes:
        stated = recipe.get('minutes')
        if not stated:
            continue
        estimated = [step for step in recipe.get('steps', []) if items.get(step['id'], {}).get('minutes')]
        if len(estimated) < 3:
            continue
        total = sum(items[step['id']]['minutes'] for step in estimated)
        ratios.append((total / stated, recipe, total, len(estimated)))
    outliers = sorted((row for row in ratios if row[0] < 0.3 or row[0] > 2.5), key=lambda row: -abs(row[0] - 1))

    regions = Counter(recipe.get('region', '无') for recipe, _, _ in rows)
    region_steps = Counter(recipe.get('region', '无') for recipe in recipes for _ in recipe.get('steps', []))

    lines = [
        '# 步骤时长提案审核',
        '',
        f'- 提案：`{args.proposal}`，模型 `{proposal.get("model", "?")}`，生成于 {proposal.get("generatedAt", "?")}',
        f'- 覆盖：{len(rows)}/{all_steps} 步有提案；类型 {kinds.get("ACTIVE", 0)} 占手、{kinds.get("PASSIVE", 0)} 等待',
    ]
    if minutes:
        lines.append(
            f'- 时长：中位数 {statistics.median(minutes):g} 分钟，'
            f'P90 {sorted(minutes)[int(len(minutes) * 0.9) - 1]} 分钟，范围 {min(minutes)}–{max(minutes)}；'
            f'缺时长 {len(without_minutes)} 步',
        )
    lines += ['', '## 分布', '', '| 时长 | 步数 |', '| --- | --- |']
    buckets = Counter(bucket(item['minutes']) for item in (item for _, _, item in rows) if item['minutes'] is not None)
    for edge in BUCKETS:
        label = f'≤{edge}'
        lines.append(f'| {label} | {buckets.get(label, 0)} |')
    lines += ['', '| 地域 | 已标注/全部 |', '| --- | --- |']
    for region, total in region_steps.most_common():
        lines.append(f'| {region} | {regions.get(region, 0)}/{total} |')

    lines += ['', '## 需要人工看的行', '']
    if active_with_hint:
        lines.append(f'- **{len(active_with_hint)} 步含等待关键词却标了占手**（校验脚本会报错）：')
        for recipe, step, _ in active_with_hint[:10]:
            lines.append(f'  - {recipe["title"]} · {step["title"]}：{step["text"][:40]}')
    if passive_with_action:
        lines.append(f'- **{len(passive_with_action)} 步标了等待但看起来要动手**（可能误判，建议抽看）：')
        for recipe, step, item in passive_with_action[:30]:
            shown = item['minutes'] if item['minutes'] is not None else '—'
            lines.append(f'  - {recipe["title"]} · {step["title"]}：{step["text"][:40]}（{shown} 分钟）')
    if passive_without_minutes:
        lines.append(f'- **{len(passive_without_minutes)} 步是等待却没有时长**（排程会按未知处理）：')
        for recipe, step, _ in passive_without_minutes[:15]:
            lines.append(f'  - {recipe["title"]} · {step["title"]}：{step["text"][:40]}')
    if outliers:
        lines.append(f'- **{len(outliers)} 道菜的步骤合计与标称总用时差得远**（比例 <0.3 或 >2.5）：')
        for ratio, recipe, total, count in outliers[:20]:
            lines.append(
                f'  - {recipe["title"]}：{count} 步合计 {total} 分钟，标称 {recipe["minutes"]} 分钟（×{ratio:.1f}）',
            )
    if not (active_with_hint or passive_with_action or passive_without_minutes or outliers):
        lines.append('- 没有发现明显可疑的行。')
    lines += ['', '## 下一步', '', '- 抽看完上面的行后，用 `python tools/apply_step_timing.py --proposal tools/step_timing.json --apply` 合并；合并后跑 `tools/validate_content.py`。', '']

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text('\n'.join(lines), encoding='utf-8')

    print(f'覆盖 {len(rows)}/{all_steps} 步；占手 {kinds.get("ACTIVE", 0)}，等待 {kinds.get("PASSIVE", 0)}')
    if minutes:
        print(f'时长中位数 {statistics.median(minutes):g} 分钟，范围 {min(minutes)}–{max(minutes)}，缺时长 {len(without_minutes)}')
    print(
        f'可疑：占手但含等待词 {len(active_with_hint)}，等待但要动手 {len(passive_with_action)}，'
        f'等待缺时长 {len(passive_without_minutes)}，合计时长离群 {len(outliers)}',
    )
    print(f'报告写入 {args.out}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
