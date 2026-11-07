"""Ask the model for a reference duration and an ACTIVE/PASSIVE kind for recipe steps.

This is phase 1 of the step-timing work: the script only *proposes* values into
`tools/step_timing.json`. Nothing under `app/src/main/assets` is written; applying the
proposal is a separate, reviewed step (see docs/交接文档.md §14).

Usage:
    python tools/propose_step_timing.py --sample 30          # 30 spread-out steps for review
    python tools/propose_step_timing.py --all                # every step; the free model is slow
    python tools/propose_step_timing.py --sample 30 --review tools/step_timing_sample.md

The API key comes from DEEPCOOK_AI_KEY (environment) or `local.properties`; it is never
written into a file this script creates.
"""
import argparse
import json
import os
import random
import statistics
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
DEFAULT_OUT = ROOT / 'tools/step_timing.json'
ENDPOINT = 'https://open.bigmodel.cn/api/paas/v4/chat/completions'
MODEL = 'glm-4.7-flash'
FALLBACK = 'glm-4.5-flash'
STEP_KINDS = ('ACTIVE', 'PASSIVE')
# Mirrors tools/validate_content.py. Deliberately narrow: "泡发好的木耳" is a state, not a
# soaking step, so bare 泡 is not a hint. The rule is a guard against obvious mistakes, not a
# classifier — the model still decides every step.
PASSIVE_HINTS = ('腌制', '腌渍', '炖', '焖', '蒸', '卤', '冷藏', '静置', '发酵', '晾凉', '冷却', '醒面', '浸泡')
SEED = 20261107

SYSTEM = """你是菜谱时间标注助手。给你一批菜谱步骤，判断每一步的「参考时长」和「占手类型」。
只输出 JSON 数组，不要解释，不要 markdown。每一项：
{"id": "步骤 id", "minutes": 整数或 null, "kind": "ACTIVE" 或 "PASSIVE", "why": "不超过 12 字的依据"}

规则：
- minutes 是从开始这一步到可以交给下一步的参考分钟数，取 1–180 的整数；纯准备/装盘 1–5 分钟，炒/煎 3–10 分钟，炖/蒸/烤按常见时长。
- kind：ACTIVE = 需要人守在灶台前（切、洗、去根、炒、煎、翻面、搅拌、调味、撒料、装盘）；PASSIVE = 整步主要是「放进去等」（腌制、浸泡、炖、焖、蒸、烤、冷藏、静置、发酵、卤）。
- 只有整步基本不用人守着才算 PASSIVE；洗、切、撒料、装盘哪怕很快也算 ACTIVE。
- 不要因为原文没写时间就不给：按常见家常做法估计，宁可保守。
- 每一项都必须用输入里给过的 id；不要新增、不要漏掉、不要改 id。"""

# Same job without the explanation field: about a third fewer output tokens for the bulk run.
SYSTEM_TERSE = """你是菜谱时间标注助手。给你一批菜谱步骤，判断每一步的「参考时长」和「占手类型」。
只输出 JSON 数组，不要解释，不要 markdown。每一项：
{"id": "步骤 id", "minutes": 整数或 null, "kind": "ACTIVE" 或 "PASSIVE"}

规则：
- minutes 是从开始这一步到可以交给下一步的参考分钟数，取 1–180 的整数；纯准备/装盘 1–5 分钟，炒/煎 3–10 分钟，炖/蒸/烤按常见时长。
- kind：ACTIVE = 需要人守在灶台前（切、洗、去根、炒、煎、翻面、搅拌、调味、撒料、装盘）；PASSIVE = 整步主要是「放进去等」（腌制、浸泡、炖、焖、蒸、烤、冷藏、静置、发酵、卤）。
- 只有整步基本不用人守着才算 PASSIVE；洗、切、撒料、装盘哪怕很快也算 ACTIVE。
- 不要因为原文没写时间就不给：按常见家常做法估计，宁可保守。
- 每一项都必须用输入里给过的 id；不要新增、不要漏掉、不要改 id。"""


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--sample', type=int, metavar='N', help='propose for N steps spread over the catalog')
    group.add_argument('--all', action='store_true', help='propose for every step (long)')
    parser.add_argument('--batch', type=int, default=10, help='steps per request (default 10)')
    parser.add_argument('--model', default=MODEL, help=f'primary model (default {MODEL})')
    parser.add_argument('--attempts', type=int, default=4, help='tries per batch before giving up (default 4)')
    parser.add_argument('--terse', action='store_true', help='drop the "why" field to generate faster')
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT, help=f'proposal file (default {DEFAULT_OUT})')
    parser.add_argument('--review', type=Path, help='also write a markdown table for human review')
    parser.add_argument('--delay', type=float, default=5.0, help='seconds to wait between retries (default 5)')
    parser.add_argument('--restart', action='store_true', help='ignore an existing proposal file')
    parser.add_argument('--dry-run', action='store_true', help='print the first request instead of sending it')
    return parser.parse_args(argv)


def api_key():
    key = os.environ.get('DEEPCOOK_AI_KEY', '').strip()
    if key:
        return key
    properties = ROOT / 'local.properties'
    if properties.is_file():
        for line in properties.read_text(encoding='utf-8').splitlines():
            if line.strip().startswith('DEEPCOOK_AI_KEY='):
                return line.split('=', 1)[1].strip()
    raise SystemExit('缺少 DEEPCOOK_AI_KEY：设置环境变量，或写进 local.properties。')


def load_steps():
    recipes = json.loads((ASSETS / 'recipes.json').read_text(encoding='utf-8'))
    steps = []
    for recipe in recipes:
        for step in recipe.get('steps', []):
            steps.append({
                'id': step['id'],
                'dish': recipe['title'],
                'title': step.get('title', ''),
                'text': step.get('text', ''),
                'cue': step.get('cue') or '',
                'order': step.get('order', 0),
            })
    return steps


def choose_sample(steps, count):
    if len(steps) <= count:
        return steps
    picked = random.Random(SEED).sample(steps, count)
    return sorted(picked, key=lambda item: (item['dish'], item['order']))


def user_prompt(batch):
    lines = [
        json.dumps(
            {'id': item['id'], 'dish': item['dish'], 'step': item['title'], 'text': item['text'], 'cue': item['cue']},
            ensure_ascii=False,
        )
        for item in batch
    ]
    return '步骤列表：\n' + '\n'.join(lines)


def request(api_key_value, model, prompt, system=SYSTEM, timeout=180, max_tokens=2000):
    payload = {
        'model': model,
        'stream': False,
        'temperature': 0.2,
        'max_tokens': max_tokens,
        'thinking': {'type': 'disabled'},
        'messages': [
            {'role': 'system', 'content': system},
            {'role': 'user', 'content': prompt},
        ],
    }
    body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    http_request = urllib.request.Request(
        ENDPOINT,
        data=body,
        method='POST',
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {api_key_value}'},
    )
    with urllib.request.urlopen(http_request, timeout=timeout) as response:
        data = json.loads(response.read().decode('utf-8'))
    return data['choices'][0]['message']['content']


def extract_items(reply):
    start, stop = reply.find('['), reply.rfind(']')
    if start >= 0 and stop > start:
        data = json.loads(reply[start:stop + 1])
        if isinstance(data, list):
            return data
    start, stop = reply.find('{'), reply.rfind('}')
    if start >= 0 and stop > start:
        data = json.loads(reply[start:stop + 1])
        if isinstance(data, dict) and isinstance(data.get('items'), list):
            return data['items']
    raise ValueError('回复里没有 JSON 数组')


def clean(batch, rows):
    """Drops anything that could not be trusted; the same rules as validate_content.py."""
    by_id = {item['id']: item for item in batch}
    result = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        step_id = row.get('id')
        if step_id not in by_id or step_id in result:
            continue
        minutes = row.get('minutes')
        if isinstance(minutes, bool) or not isinstance(minutes, int) or not 1 <= minutes <= 180:
            minutes = None
        kind = row.get('kind')
        if kind not in STEP_KINDS:
            continue
        words = by_id[step_id]['title'] + by_id[step_id]['text'] + by_id[step_id]['cue']
        hint = next((word for word in PASSIVE_HINTS if word in words), None)
        corrected = bool(hint) and kind == 'ACTIVE'
        if corrected:
            kind = 'PASSIVE'
        result[step_id] = {
            'minutes': minutes,
            'kind': kind,
            'why': str(row.get('why', '')).strip()[:30],
            'corrected': corrected,
        }
    return result


def save(path, items, mode, model):
    payload = {
        'generatedAt': datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'model': model,
        'source': 'ai-draft',
        'mode': mode,
        'items': dict(sorted(items.items())),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')


def write_review(path, steps, items):
    rows = [
        '| 菜名 | 步骤 | 原文 | 分钟 | 类型 | 依据 |',
        '| --- | --- | --- | --- | --- | --- |',
    ]
    for step in steps:
        item = items.get(step['id'])
        if not item:
            continue
        minutes = '' if item['minutes'] is None else str(item['minutes'])
        text = (step['text'] or '').replace('|', '／').replace('\n', ' ')
        if len(text) > 60:
            text = text[:60] + '…'
        kind = '等待' if item['kind'] == 'PASSIVE' else '占手'
        if item.get('corrected'):
            kind += '（已纠正）'
        rows.append(f"| {step['dish']} | {step['title']} | {text} | {minutes} | {kind} | {item['why']} |")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(rows) + '\n', encoding='utf-8')


def main(argv=None):
    args = parse_args(argv)
    steps = load_steps()
    target = steps if args.all else choose_sample(steps, args.sample)
    mode = 'all' if args.all else f'sample-{args.sample}'
    key = api_key()

    existing = {}
    if args.out.is_file() and not args.restart:
        try:
            existing = json.loads(args.out.read_text(encoding='utf-8')).get('items', {})
        except (ValueError, OSError):
            existing = {}
    pending = [step for step in target if step['id'] not in existing]
    print(f'目标 {len(target)} 步，已有 {len(existing)} 条，本次需要 {len(pending)} 条。')

    if args.dry_run:
        batch = pending[:args.batch]
        print('--- 系统提示 ---')
        print(SYSTEM_TERSE if args.terse else SYSTEM)
        print('--- 用户消息 ---')
        print(user_prompt(batch))
        return 0

    failures = []
    for start in range(0, len(pending), args.batch):
        batch = pending[start:start + args.batch]
        prompt = user_prompt(batch)
        cleaned = None
        ladder = [args.model, FALLBACK, FALLBACK, FALLBACK][: max(1, args.attempts)]
        for attempt, model in enumerate(ladder):
            try:
                reply = request(
                    key,
                    model,
                    prompt,
                    system=SYSTEM_TERSE if args.terse else SYSTEM,
                    max_tokens=max(1200, len(batch) * 140),
                )
                cleaned = clean(batch, extract_items(reply))
                break
            except urllib.error.HTTPError as error:
                detail = error.read().decode('utf-8', 'replace')[:160]
                print(f'  HTTP {error.code}（{model}）：{detail}', flush=True)
                if error.code == 429:
                    time.sleep(args.delay * (attempt + 1))
                else:
                    break
            except (urllib.error.URLError, TimeoutError, ValueError, KeyError) as error:
                print(f'  请求失败（{model}）：{error}', flush=True)
                time.sleep(args.delay * (attempt + 1))
        if not cleaned:
            failures.append([step['id'] for step in batch])
            print(f'  批次 {start // args.batch + 1} 放弃（{len(batch)} 步）', flush=True)
            continue
        missing = [step['id'] for step in batch if step['id'] not in cleaned]
        existing.update(cleaned)
        save(args.out, existing, mode, args.model)
        print(
            f'  批次 {start // args.batch + 1}/{(len(pending) + args.batch - 1) // args.batch}：'
            f'收下 {len(cleaned)}，缺 {len(missing)}',
            flush=True,
        )

    save(args.out, existing, mode, args.model)
    collected = {step['id']: existing[step['id']] for step in target if step['id'] in existing}
    if args.review:
        write_review(args.review, target, collected)

    kinds = {'ACTIVE': 0, 'PASSIVE': 0}
    for item in collected.values():
        kinds[item['kind']] += 1
    minutes = [item['minutes'] for item in collected.values() if item['minutes'] is not None]
    print(f'完成：{len(collected)}/{len(target)} 步写入 {args.out}')
    print(f'类型：占手 {kinds["ACTIVE"]}，等待 {kinds["PASSIVE"]}')
    if minutes:
        print(
            f'时长：中位数 {statistics.median(minutes):g} 分钟，'
            f'范围 {min(minutes)}–{max(minutes)}，缺少时长 {len(collected) - len(minutes)} 步',
        )
    if failures:
        print(f'失败批次：{len(failures)}（共 {sum(len(batch) for batch in failures)} 步）')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
