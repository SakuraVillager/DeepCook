"""Build conservative, reproducible estimates from explicit quantities and CoFID 2021.

No network is needed: nutrition-foods.json contains attributed source rows. Counts
without a weight, ambiguous ingredients and unknown main ingredients are not guessed.
"""
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KEYS = ('calories', 'fat', 'protein', 'carbs', 'fiber', 'sodium')
UNITS = {'kg': 1000, '千克': 1000, '公斤': 1000, 'g': 1, '克': 1,
         '斤': 500, '两': 50, 'ml': 1, '毫升': 1, 'l': 1000, '升': 1000}
QUANTITY = re.compile(r'(?<![\d.])(\d+(?:\.\d+)?)\s*(?:[-~～—–至到]\s*(\d+(?:\.\d+)?))?\s*'
                      r'(kg|千克|公斤|ml|毫升|g|克|斤|两|l|升)(?![a-z])', re.I)
MINOR = re.compile(r'^(?:姜|生姜|姜片|姜末|蒜|大蒜|蒜瓣|香葱|小葱|葱|葱段|'
                   r'香菜|八角|香叶|桂皮|花椒|干辣椒|料酒|黄酒)$')
HEADINGS = {'必须配料', '必需配料', '必备配料', '可选配料', '主料', '辅料', '配料'}


def normalized(value):
    return unicodedata.normalize('NFKC', value).strip().lower()


def quantity(value):
    """Return grams (volume uses a documented 1 ml ≈ 1 g approximation)."""
    matches = list(QUANTITY.finditer(normalized(value)))
    if not matches:
        return None
    values = []
    for match in matches:
        low = float(match[1])
        high = float(match[2]) if match[2] else low
        if not 0 < low <= high:
            return None
        grams = (low + high) / 2 * UNITS[match[3].lower()]
        if grams > 100000:
            return None
        values.append((grams, match[3].lower() in ('ml', '毫升', 'l', '升'), low != high))
    # Parenthesized equivalents such as 1 kg (1000 g) are fine; alternatives are not.
    if any(abs(v[0] - values[0][0]) > .001 for v in values[1:]):
        return None
    return values[0]


def serving_count(value):
    text = normalized(value)
    # A human count is required. Bowls, portions without a number and weights are not people.
    match = re.search(r'(\d+(?:\.\d+)?)\s*(?:[-~～—–至到]\s*(\d+(?:\.\d+)?))?\s*人', text)
    if not match:
        match = re.search(r'([一二三四五六七八九十])\s*人', text)
        return float('一二三四五六七八九十'.index(match[1]) + 1) if match else None
    low, high = float(match[1]), float(match[2] or match[1])
    return (low + high) / 2 if 0 < low <= high <= 100 else None


def piece_quantity(value, food):
    piece = food.get('piece') if food else None
    if not piece:
        return None
    number = r'(\d+(?:\.\d+)?|[一二三四五六七八九十两半])'
    match = re.search(number + r'\s*(?:[-~～—–至到]\s*' + number + r')?\s*(' + '|'.join(piece['units']) + ')', normalized(value))
    if not match:
        return None
    def numeric(text):
        if text == '半': return .5
        if text == '两': return 2
        return float('一二三四五六七八九十'.index(text)+1) if text in '一二三四五六七八九十' else float(text)
    low, high = numeric(match[1]), numeric(match[2] or match[1])
    return ((low+high)/2*piece['grams'], False, low != high) if 0 < low <= high <= 100 else None


def recipe_key(recipe):
    return [recipe.get('servings', '')] + [v for i in recipe['ingredients'] for v in (i['name'], i['amount'])]


def match_food(name, foods):
    clean = re.sub(r'^[\d一二三四五六七八九十两半.]+\s*(?:[-~～—]\s*[\d.]+)?\s*(?:kg|公斤|千克|g|克|盒|块|枚|个|颗|根|棵)\s*', '', name)
    matches = [(len(alias), food) for food in foods for alias in food['aliases']
               if re.fullmatch(re.escape(alias) + r'(?:末|片|丁|切片|切丁|切块|去皮|去骨)?', clean)]
    if not matches:
        return None
    # A mixed ingredient must not be silently assigned to whichever token happens to match.
    if re.search(r'[,，、/或和与及]', name):
        return None
    length = max(n for n, _ in matches)
    longest = {food['code']: food for n, food in matches if n == length}
    return next(iter(longest.values())) if len(longest) == 1 else None


def estimate(recipe, foods):
    totals = {key: 0.0 for key in KEYS}
    excluded, used = [], []
    volume, ranged, pieces = False, False, False
    for ingredient in recipe['ingredients']:
        name = normalized(re.split(r'[（(]', ingredient['name'])[0])
        amount = ingredient['amount']
        if name in HEADINGS or name in {'水', '清水', '开水', '热水', '凉水', '冷水', '饮用水'}:
            continue
        measured = quantity(amount)
        # Sometimes the amount field is empty, with the explicit mass in the ingredient name.
        if measured is None and amount.strip() in ('无', ''):
            measured = quantity(ingredient['name'])
        food = match_food(name, foods)
        if measured is None and food:
            measured = piece_quantity(amount, food)
            if measured is None and amount.strip() in ('无', ''):
                measured = piece_quantity(ingredient['name'], food)
            pieces |= measured is not None
        if '可选' in ingredient['name'] or '可选' in amount:
            excluded.append(ingredient['name'])
            continue
        if measured is None and name in {'盐', '食盐', '食用盐'}:
            totals['sodium'] = None
            excluded.append(ingredient['name'])
            continue
        if MINOR.fullmatch(name) and (measured is None or (food is None and measured[0] <= 15)):
            # Exclusions are disclosed; never infer a weight from cloves, slices or pieces.
            count = re.search(r'\d+(?:\.\d+)?', normalized(amount))
            if measured is None and count and float(count[0]) > 10:
                return None, '辅料用量无法确定'
            excluded.append(ingredient['name'])
            if name in {'料酒', '黄酒'}:
                totals['sodium'] = None  # Salt content varies by cooking-wine product.
            continue
        if measured is None or food is None:
            return None, '主要食材未匹配或重量不明确'
        grams, is_volume, is_range = measured
        volume |= is_volume
        ranged |= is_range
        used.append(food['code'])
        for key in KEYS:
            value = food['per100'][key]
            totals[key] = None if value is None or totals[key] is None else totals[key] + value * grams / 100
    if not used or totals['calories'] is None or totals['calories'] <= 0:
        return None, '缺少可估算的主要食材'
    servings = serving_count(recipe.get('servings', ''))
    divisor = servings or 1
    values = {key: None if total is None else round(total / divisor, 0 if key in ('calories', 'sodium') else 1)
              for key, total in totals.items()}
    for key in ('calories', 'sodium'):
        if values[key] is not None:
            values[key] = int(values[key])
    notes = ['营养数据为大致估算，仅供饮食参考；实际数值随食材、份量和烹饪方式变化。']
    values.update(basis='serving' if servings else 'recipe', servings=servings,
                  note=''.join(notes))
    return {'title': recipe['title'], 'recipeKey': recipe_key(recipe), 'values': values, 'foodCodes': sorted(set(used)),
            'assumptions': {'volumeApproximation': volume, 'standardPieceWeight': pieces,
                            'rangeMidpoint': ranged, 'excludedIngredients': list(dict.fromkeys(excluded))}}, None


def build():
    reference = json.loads((ROOT / 'tools/nutrition-foods.json').read_text())
    recipes = json.loads((ROOT / 'app/src/main/assets/recipes.json').read_text())
    estimates, unavailable = {}, {}
    for recipe in recipes:
        entry, reason = estimate(recipe, reference['foods'])
        if entry:
            estimates[recipe['id']] = entry
        else:
            unavailable[recipe['id']] = reason
    output = {'schema': 1, 'source': reference['source'], 'recipes': estimates}
    (ROOT / 'app/src/main/assets/nutrition_estimates.json').write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'estimated': len(estimates), 'notEstimated': len(unavailable),
                      'perServing': sum(v['values']['basis'] == 'serving' for v in estimates.values())}))


if __name__ == '__main__':
    build()
