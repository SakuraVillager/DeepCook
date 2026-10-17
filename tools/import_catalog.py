"""Import pinned, public-domain recipe sources; never execute source content.

fetch: cache source Markdown. build: normalize Chinese recipes and checked-in
Chinese translations, download/resize referenced photos, and produce an audit.
Raw inputs and the network cache stay in ignored artifacts/content-import.
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
import posixpath
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'artifacts/content-import'
ASSETS = ROOT / 'app/src/main/assets'
TRANSLATIONS = ROOT / 'tools/world_recipes_zh.json'
PINNED = {
    'howtocook': ('Anduin2017/HowToCook', '743d7f46b7644c43673e0b2e255c745f6fa8ca5e'),
    'based': ('LukeSmithxyz/based.cooking', '9d4a31a040eedd61e4fb608cb0c114ff9a7c4dd2'),
}
WORLD_SLUGS = [
    'pasta-arrabbiata', 'greek-salad', 'spanish-tortilla', 'ratatouille',
    'beef-goulash', 'schnitzel', 'swedish-pancakes', 'finnish-salmon-soup',
    'danish-pancake', 'irish-potato-casserole', 'tunisian-tajine',
    'lebanese-lentil-soup', 'hummus', 'shakshouka', 'miso-soup',
    'pho-soup', 'panang-style-beef-curry', 'chicken-tikka-masala',
    'naan-bread', 'fajitas', 'brigadeiro', 'pavlova', 'ukrainian-borscht', 'tzatziki',
]
LEGACY = {
    '简易红烧肉': ('braised-pork', '红烧肉'), '炒滑蛋': ('silky-eggs', '葱香滑蛋'),
    '宫保鸡丁': ('kung-pao', '宫保鸡丁'), '清蒸鲈鱼': ('steamed-fish', '清蒸鲈鱼'),
    '地三鲜': ('three-treasures', '地三鲜'), '玉米排骨汤': ('corn-soup', '玉米排骨汤'),
    '麻婆豆腐': ('mapo-tofu', '麻婆豆腐'), '干锅花菜': ('cauliflower', '干锅花菜'),
    '白灼菜心': ('greens', '白灼菜心'), '手撕包菜': ('cabbage', '手撕包菜'),
    '油焖大虾': ('shrimp', '油焖大虾'), '西红柿豆腐汤羹': ('tomato-tofu', '番茄豆腐汤'),
}
FOLDER_CATEGORIES = {
    'aquatic': ['海鲜'], 'meat_dish': ['肉类'], 'vegetable_dish': ['素食'],
    'soup': ['汤羹'], 'staple': ['主食'], 'breakfast': ['早餐'],
    'dessert': ['甜点'], 'drink': ['饮品'], 'semi-finished': ['半成品'], 'condiment': ['酱料'],
}


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def source(key):
    path = CACHE / f'{key}-tree.json'
    repository, commit = PINNED[key]
    if not path.exists():
        request = urllib.request.Request(f'https://api.github.com/repos/{repository}/git/trees/{commit}?recursive=1', headers={'User-Agent': 'DeepCook-content-import/0.2'})
        tree = json.load(urllib.request.urlopen(request, timeout=60))
        if tree.get('truncated'):
            raise RuntimeError('Source tree was truncated')
        write_json(path, dict(repository=repository, commit=commit, **tree))
    result = read_json(path)
    assert result['commit'] == commit and result['repository'] == repository
    return result


def raw_url(tree, path):
    return f"https://raw.githubusercontent.com/{tree['repository']}/{tree['commit']}/" + urllib.parse.quote(path)


def fetch(tree, path):
    cached = CACHE / 'raw' / tree['commit'] / hashlib.sha256(path.encode()).hexdigest()
    if cached.exists():
        return cached.read_bytes()
    error = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(raw_url(tree, path), headers={'User-Agent': 'DeepCook-content-import/0.1'})
            data = urllib.request.urlopen(request, timeout=35).read()
            cached.parent.mkdir(parents=True, exist_ok=True)
            cached.write_bytes(data)
            return data
        except Exception as caught:
            error = caught
            time.sleep(attempt + 1)
    raise RuntimeError(f'{path}: {error}')


def plain(text):
    text = re.sub(r'!\[[^\]]*\]\([^)]*\)', '', text)
    text = re.sub(r'\[([^\]]+)\]\(([^)]*)\)', r'\1', text)
    text = re.sub(r'<[^>]+>', '', text)
    text = text.replace('**', '').replace('`', '')
    text = re.sub(r'(?<![\w\d])\*([^*\n]+)\*(?![\w\d])', r'\1', text)
    return text.strip()


def sections(text):
    result = {}
    name = '简介'
    result[name] = []
    for line in text.splitlines():
        match = re.match(r'^##\s+(.+?)\s*$', line)
        if match:
            name = plain(match.group(1))
            result.setdefault(name, [])
        else:
            result[name].append(line)
    return result


def list_items(lines):
    items = []
    current = []
    heading = ''
    for line in lines:
        if re.match(r'^#{3,}\s+', line):
            if current:
                items.append('\n'.join(current).strip())
                current = []
            heading = re.sub(r'^#+\s+', '', line).strip()
            continue
        match = re.match(r'^(\s*)(?:[-+*]|\d+[.、)])\s+(.*)', line)
        if match and len(match.group(1)) < 2:
            if current:
                items.append('\n'.join(current).strip())
            current = [match.group(2)]
            if heading:
                current[0] = heading + '：' + current[0]
                heading = ''
        elif line.strip():
            if not current:
                current = [line.strip()]
            else:
                current.append(line.strip())
    if current:
        items.append('\n'.join(current).strip())
    return [plain(item) for item in items if plain(item)]


def ingredient_rows(lines):
    result = []
    for line in lines:
        line = line.strip()
        if line.startswith('|'):
            cells = [plain(x) for x in line.strip('|').split('|')]
            if len(cells) < 2 or re.fullmatch(r'[:\- ]+', cells[0]) or cells[0] in ('原料', '食材', '材料', '名称', '配料', '食材名称'):
                continue
            if cells[0]:
                result.append((cells[0], ' '.join(x for x in cells[1:] if x) or '无'))
            continue
        match = re.match(r'^[-*+]\s+(.*)', line)
        if not match:
            continue
        item = plain(match.group(1))
        if not item or re.match(r'^\d+\s*(?:勺|茶匙|汤匙|杯).*[=＝]', item):
            continue
        if item in ('工具', '原料', '食材') or item.startswith(('注：', '注:', '工具：', '工具:', '一个装成品的容器', '可密封容器')):
            continue
        if item.startswith(('主料：', '主料:')):
            result.extend(ingredient_rows(['- ' + p for p in re.split('[、，]', item[3:])]))
            continue
        if item.startswith(('可选：', '可选:')):
            item = item[3:] + '（可选）'
        colon = re.match(r'^([^：:]+)[：:]\s*(.+)$', item)
        formula = re.match(r'^(.+?)\s*[=＝]\s*(.+)$', item)
        chinese_amount = re.match(r'^(.+?)\s+([一二两三四五六七八九十百半]+\s*(?:条|根|块|片|只|个|颗|头|斤|克|勺|杯|把|碗|袋|包|盒|罐|汤匙|茶匙).*)$', item)
        amount = re.search(r'(?<![A-Za-z])\d+(?:\.\d+)?', item)
        leading = re.match(r'^((?:\d+(?:\.\d+)?|[一二两三四五六七八九十]+)\s*(?:ml|mL|g|克|毫升|个|颗|头|杯))\s*(.+)$', item)
        if formula:
            name, value = formula.group(1).strip(), formula.group(2).strip()
        elif colon:
            name, value = colon.group(1).strip(), colon.group(2).strip()
        elif leading:
            value, name = leading.group(1).strip(), leading.group(2).strip()
        elif chinese_amount:
            name, value = chinese_amount.group(1).strip(), chinese_amount.group(2).strip()
        elif amount and amount.start() > 0:
            name = item[:amount.start()].strip(' ：:（(')
            value = item[amount.start():].strip()
        else:
            vague = re.search(r'(适量|少许|若干|按需|根据口味)', item)
            name, value = (item[:vague.start()].strip(), item[vague.start():]) if vague else (item, '无')
        if name:
            expression = re.search(r'[（(]?\s*(?:可选[，,]\s*)?份数\\?\*|\s*\*\s*份数', name)
            if expression:
                value = name[expression.start():] + ' ' + ('' if value == '无' else value)
                name = name[:expression.start()].strip()
            result.append((name, value))
    return result


def chinese_number(value):
    if re.fullmatch(r'\d+(?:\.\d+)?', value):
        return float(value)
    if value == '半':
        return 0.5
    digits = {'零': 0, '〇': 0, '一': 1, '二': 2, '两': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9}
    total, digit = 0, 0
    for char in value:
        if char in digits:
            digit = digits[char]
        elif char in ('十', '百'):
            total += (digit or 1) * (10 if char == '十' else 100)
            digit = 0
        else:
            raise ValueError('Unsupported duration numeral')
    return total + digit


def total_minutes(intro):
    # Only explicit whole-recipe statements; never add up individual steps.
    context = r'(?:总耗时|总用时|预计(?:制作)?(?:时长|用时|耗时)?|预估制作时长|全程(?:制作)?(?:（[^）]*）)?|从(?:备料|准备|刷洗|处理活虾)到(?:出锅|上桌|成菜|完成))'
    number = r'[\d.零〇一二两三四五六七八九十百半]+'
    match = re.search(context + r'[^\n。；;\d零〇一二两三四五六七八九十百半]{0,18}(' + number + r')(\s*[–—~～至-]\s*' + number + r')?\s*(?:个)?(分钟|小时)(半)?', intro)
    if not match or match.group(2):
        return None
    amount = chinese_number(match.group(1))
    result = amount * (60 if match.group(3) == '小时' else 1)
    if match.group(4):
        result += 30 if match.group(3) == '小时' else 0.5
    # A compound duration cannot be truncated to its first hour field.
    tail = intro[match.end():]
    extra = re.match(r'\s*(?:零|又)?\s*(' + number + r')\s*分钟', tail)
    if extra and match.group(3) == '小时':
        result += chinese_number(extra.group(1))
    return round(result) if result > 0 else None


def chinese_recipe(tree, path):
    if path.startswith('dishes/template/'):
        raise ValueError('仓库格式模板，不是真实菜谱')
    raw = fetch(tree, path).decode('utf-8-sig')
    parts = sections(raw)
    title_match = re.search(r'^#\s+(.+)', raw, re.M)
    if not title_match:
        raise ValueError('缺少标题')
    title = plain(title_match.group(1))
    title = re.sub(r'的(?:做法|制作方法|制作)$', '', title)
    instruction_key = next((k for k in parts if k in ('操作', '操作步骤', '制作步骤', '做法')), None)
    ingredients_key = next((k for k in parts if k in ('计算', '原料用量', '食材用量', '用量')), None)
    required_key = next((k for k in parts if '原料' in k or '食材' in k), None)
    if instruction_key is None:
        raise ValueError('缺少可识别操作章节')
    steps = list_items(parts[instruction_key])
    ingredients = ingredient_rows(parts.get(ingredients_key, [])) if ingredients_key else []
    if not ingredients and required_key:
        ingredients = ingredient_rows(parts[required_key])
    if not steps or not ingredients:
        raise ValueError('缺少材料或有效步骤')
    original_title = title
    recipe_id, title = LEGACY.get(title, ('htc-' + hashlib.sha256(path.encode()).hexdigest()[:16], title))
    intro = '\n'.join(parts.get('简介', []))
    stars = re.search(r'烹饪难度[：:\s]*([★⭐]{1,5})', intro)
    minutes = total_minutes(intro)
    servings_match = re.search(r'(?:可供|供|估计|够|按)[^\n。]{0,12}?([一二三四五六七八九十\d]+(?:[—~～至-][一二三四五六七八九十\d]+)?)[\s个]*人(?:食用|食|份|的量|量|吃)', '\n'.join(parts.get(ingredients_key, [])))
    images = []
    image_priorities = {}
    known_paths = {entry['path'] for entry in tree['tree'] if entry['type'] == 'blob'}
    for alt, image in re.findall(r'!\[([^\]]*)\]\(([^)]+)\)', raw):
        candidate = urllib.parse.unquote(image.split(' "')[0].strip('<>'))
        if candidate.startswith(('http:', 'https:')):
            continue
        candidate = posixpath.normpath(posixpath.join(posixpath.dirname(path), candidate))
        if candidate in known_paths and candidate.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
            images.append(candidate)
            basename = posixpath.splitext(posixpath.basename(candidate))[0]
            image_priorities[candidate] = 2 if basename == original_title else (1 if '成品' in alt else 0)
    images.sort(key=lambda p: -image_priorities.get(p, 0))
    # Some source images are siblings referenced by the directory README.
    if not images:
        parent = posixpath.dirname(path)
        if len([p for p in known_paths if posixpath.dirname(p) == parent and p.endswith('.md')]) == 1:
            images = sorted(p for p in known_paths if posixpath.dirname(p) == parent and p.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')))
    categories = ['家常菜'] + FOLDER_CATEGORIES.get(path.split('/')[1], [])
    if minutes is not None and minutes <= 30:
        categories.append('快手菜')
    # Preserve plain required ingredients absent from the quantity section.
    if required_key and ingredients_key:
        for name, amount in ingredient_rows(parts[required_key]):
            short = re.split(r'[（(：:]', name)[0].strip()
            if short and not any(word in name for word in ['工具', '注：', '主料', '辅料', '锅', '刀', '碗', '勺', '铲', '烤箱', '料理机', '电饭煲', '温度计']):
                if not any(short in existing or existing in short for existing, _ in ingredients):
                    ingredients.append((name, amount))
    notes = list_items(parts.get('附加内容', []))
    notes = [n for n in notes if not any(x in n for x in ['遵循本指南', 'Issue', 'Pull request', '其他内容请通过'])]
    recipe = dict(id=recipe_id, version=3, title=title, aliases=[original_title] if original_title != title else [], description='收录自 HowToCook，按中文原文整理。',
                  cover=None, categories=categories, minutes=minutes, difficulty=len(stars.group(1)) if stars else None,
                  servings=servings_match.group(1)+' 人份' if servings_match else '无',
                  ingredients=[dict(id=f'i{n}', name=name, amount=amount) for n, (name, amount) in enumerate(ingredients)],
                  preparation=[], steps=[dict(id=f'{recipe_id}-s{n+1}', order=n+1, title=f'步骤 {n+1}', text=s) for n, s in enumerate(steps)],
                  notes=notes, source=dict(author='HowToCook 社区贡献者', license='Unlicense · 中文原文整理，未经 DeepCook 团队逐道试做。', url='https://github.com/'+tree['repository']+'/blob/'+tree['commit']+'/'+urllib.parse.quote(path)),
                  featured=False, collection='HowToCook')
    return recipe, images[:1], path


def fetch_sources():
    htc = source('howtocook')
    based = source('based')
    jobs = [(htc, x['path']) for x in htc['tree'] if x['type'] == 'blob' and x['path'].startswith('dishes/') and x['path'].endswith('.md')]
    jobs += [(based, f'content/{slug}.md') for slug in WORLD_SLUGS]
    errors = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(fetch, t, p): p for t, p in jobs}
        for n, future in enumerate(concurrent.futures.as_completed(futures), 1):
            try:
                future.result()
            except Exception as error:
                errors.append(str(error))
            if n % 40 == 0 or n == len(jobs):
                print(f'Cached {n}/{len(jobs)} Markdown files', flush=True)
    write_json(CACHE/'fetch-errors.json', errors)
    for tree, name, target in [(htc,'LICENSE','HOWTOCOOK-LICENSE.txt'),(based,'LICENSE.md','BASED-COOKING-LICENSE.txt')]:
        (ASSETS/target).write_bytes(fetch(tree,name))
    # Human-readable primary source packet for translating selected world recipes.
    packet = '\n\n'.join('=== '+slug+' ===\n'+fetch(based,f'content/{slug}.md').decode('utf-8-sig') for slug in WORLD_SLUGS)
    (CACHE/'world-source-packet.md').write_text(packet,encoding='utf-8')
    print(f'Fetch errors: {len(errors)}', flush=True)


def build():
    htc = source('howtocook')
    based = source('based')
    records = []
    excluded = []
    for item in htc['tree']:
        path = item['path']
        if item['type'] != 'blob' or not path.startswith('dishes/') or not path.endswith('.md'):
            continue
        try:
            records.append((htc, *chinese_recipe(htc, path)))
        except Exception as error:
            excluded.append(dict(path=path, reason=str(error)))
    for entry in read_json(TRANSLATIONS):
        slug = entry.pop('slug')
        path = f'content/{slug}.md'
        recipe_id = 'world-' + slug
        entry.update(id=recipe_id, version=1, cover=None, difficulty=None, featured=False, collection='Based Cooking')
        entry['ingredients'] = [dict(id=f'i{n}', name=i[0], amount=i[1] or '无') for n, i in enumerate(entry['ingredients'])]
        entry['steps'] = [dict(id=f'{recipe_id}-s{n+1}', order=n+1, title=f'步骤 {n+1}', text=text) for n,text in enumerate(entry['steps'])]
        entry.setdefault('preparation',[])
        entry.setdefault('notes',[])
        entry['source'] = dict(author=entry.pop('author','Based Cooking 社区贡献者'), license='Unlicense · 原文由 DeepCook 翻译为中文；未经团队逐道试做。',url='https://github.com/'+based['repository']+'/blob/'+based['commit']+'/'+path)
        raw = fetch(based, path).decode('utf-8-sig')
        candidates = ['static' + p for p in re.findall(r'!\[[^\]]*\]\((/pix/[^)]+)\)', raw)]
        candidates += [f'static/pix/{slug}.webp']
        available = {x['path'] for x in based['tree'] if x['type']=='blob'}
        records.append((based, entry, [p for p in candidates if p in available][:1], path))
    ids = [r[1]['id'] for r in records]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate recipe IDs; inspect legacy mappings')
    photos = {}
    photo_errors = []
    def photo_job(tree,path):
        asset = 'photos/import-' + hashlib.sha256((tree['repository']+'/'+path).encode()).hexdigest()[:20] + '.jpg'
        dest = ASSETS/asset
        if not dest.exists():
            image = ImageOps.exif_transpose(Image.open(io.BytesIO(fetch(tree,path)))).convert('RGB')
            image.thumbnail((800,600))
            image.save(dest,quality=76,optimize=True)
        return asset
    photo_jobs = {(t['repository'], p): (t,p) for t,r,images,path in records for p in images}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        pending={pool.submit(photo_job,t,p): (t,p) for t,p in photo_jobs.values()}
        for n, future in enumerate(concurrent.futures.as_completed(pending),1):
            t,p=pending[future]
            try:
                photos[(t['repository'],p)] = future.result()
            except Exception as error:
                photo_errors.append(dict(path=p,reason=str(error)))
            if n%40==0 or n==len(pending):
                print(f'Prepared {n}/{len(pending)} photos',flush=True)
    catalog=[]
    provenance=[]
    for t,r,images,path in records:
        if images and (t['repository'],images[0]) in photos:
            r['cover']=photos[(t['repository'],images[0])]
            r['source']['imageSource']=raw_url(t,images[0])
        catalog.append(r)
        provenance.append(dict(recipeId=r['id'],repository=t['repository'],commit=t['commit'],path=path,url=r['source']['url'],license='Unlicense',originalSha256=hashlib.sha256(fetch(t,path)).hexdigest(),imagePath=images[0] if r['cover'] else None,bundled=r['cover'],imageUrl=r['source'].get('imageSource')))
    order={pair[0]:n for n,pair in enumerate(LEGACY.values())}
    catalog.sort(key=lambda r: (0,order[r['id']]) if r['id'] in order else (1 if r['id'].startswith('world-') else 2,r['title']))
    featured=['braised-pork','silky-eggs','kung-pao','world-spanish-tortilla','world-pasta-arrabbiata','world-brigadeiro']
    for r in catalog:
        r['featured']=r['id'] in featured
    write_json(ASSETS/'recipes.json',catalog)
    write_json(ASSETS/'catalog_sources.json',dict(sources=[{k:t[k] for k in ['repository','commit']} for t in [htc,based]],recipes=provenance))
    write_json(CACHE/'import-report.json',dict(total=len(catalog),chinese=sum(r['collection']=='HowToCook' for r in catalog),international=sum(r['collection']=='Based Cooking' for r in catalog),withPhotos=sum(bool(r['cover']) for r in catalog),missingTime=sum(r['minutes'] is None for r in catalog),missingDifficulty=sum(r['difficulty'] is None for r in catalog),excluded=excluded,photoErrors=photo_errors,legacyMissing=sorted(set(order)-{r['id'] for r in catalog})))
    print(json.dumps({k:v for k,v in read_json(CACHE/'import-report.json').items() if k not in ['excluded','photoErrors']},ensure_ascii=False),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('mode',choices=['fetch','build'])
    args=parser.parse_args()
    fetch_sources() if args.mode=='fetch' else build()
