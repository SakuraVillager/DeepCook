"""Prepare original demo recipes and pinned, attributed HowToCook photographs.

Run only when intentionally updating the bundled demo content. No runtime network is used.

The committed `app/src/main/assets/recipes.json` is the single source of truth: it also holds
the expansion catalog (see docs/菜谱扩展1.md). This script rebuilds ONLY the twelve original
recipes, so it would drop the expansions — it therefore refuses to run unless
DEEPCOOK_REBUILD_CONTENT=1 is set, and the expansions must be re-merged afterwards.
"""
import concurrent.futures
import io
import json
import os
from pathlib import Path
import urllib.parse
import urllib.request

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
ASSETS.mkdir(parents=True, exist_ok=True)
(ASSETS / "photos").mkdir(exist_ok=True)

# Content convention: every displayable text element is present, missing ones use "无".
NO_CONTENT = "无"

def recipe(slug, title, description, categories, minutes, difficulty, servings, ingredients, prep, steps, notes, photo):
    return dict(
        id=slug, version=3, region="中式", title=title, description=description,
        categories=categories.split("/"), minutes=minutes,
        difficulty={"简单": 1, "中等": 3, "较难": 5}.get(difficulty, difficulty), servings=servings,
        ingredients=[dict(id=f"i{n}", name=name, amount=amount) for n, (name, amount) in enumerate(ingredients)],
        preparation=prep or [NO_CONTENT],
        steps=[dict(id=f"{slug}-s{n+1}", order=n+1, title=s[0], text=s[1], cue=s[2] if len(s) > 2 and s[2] else NO_CONTENT)
               for n, s in enumerate(steps)],
        notes=notes or [NO_CONTENT], source=dict(author="DeepCook 自编示例", license="菜谱文字为自编演示内容，未经独立试做验证。配图来自 HowToCook，按仓库 Unlicense 声明使用。", imageSource=photo),
        featured=False, _photo=photo,
    )

RECIPES = [
    recipe("braised-pork", "红烧肉", "慢火煨出酱香，让平常的一餐也有值得期待的滋味。", "家常菜/肉类", 75, "中等", "原始份量 · 3—4 人份",
        [("五花肉", "500 克"), ("生姜", "4 片"), ("小葱", "2 根"), ("冰糖", "20 克"), ("生抽", "2 汤匙"), ("老抽", "1 茶匙"), ("料酒", "1 汤匙"), ("八角", "1 颗"), ("热水", "适量")],
        ["将生熟食材分开处理，准备有盖的炖锅。", "五花肉切成约 3 厘米见方的块，姜切片，葱打结。"],
        [("焯洗肉块", "五花肉冷水入锅，加入一半姜片和料酒。煮出浮沫后捞出，用温水洗净并充分沥干。", "表面浮沫已去除；焯水不代表肉块已熟透。"),
         ("煸出油脂", "锅中不额外放油，小火煸炒肉块，让各面略微上色。倒出多余油脂，保留少量底油。"),
         ("调入酱汁", "加入冰糖，小火翻炒至融化，再放入剩余姜片、葱结、八角、生抽和老抽，翻炒均匀。"),
         ("加水慢炖", "倒入没过肉块的热水，煮开后盖上锅盖转小火，炖约 50—60 分钟。过程中留意水量，必要时添加热水。", "肉块逐渐变软，可用筷子检查软烂程度。"),
         ("收汁装盘", "确认肉块已熟透且达到喜欢的软烂程度后，取出葱结，开盖转中火收汁。轻轻翻动，汤汁包裹肉块后关火。")],
        ["糖融化时保持小火，避免焦苦。", "炖煮时间随肉块大小和锅具变化，请结合实际状态判断。"], "dishes/meat_dish/红烧肉/000.jpg"),
    recipe("silky-eggs", "葱香滑蛋", "一口柔软的蛋香，给忙碌的日子留一道简单好菜。", "家常菜/快手菜", 10, "简单", "原始份量 · 2 人份",
        [("鸡蛋", "4 个"), ("小葱", "1 根"), ("温水", "2 汤匙"), ("盐", "少许"), ("食用油", "1 汤匙")], [],
        [("调好蛋液", "鸡蛋打入碗中，加入温水和盐，搅打至蛋白蛋黄充分融合。小葱洗净切成葱花。"),
         ("温油下锅", "不粘锅中加入食用油，以中小火加热。倒入蛋液，等待边缘开始凝固。"),
         ("轻推蛋液", "用锅铲从边缘向中间轻推，让未凝固的蛋液流向锅底。保持中小火，避免大幅翻炒。"),
         ("撒葱出锅", "撒入葱花，轻轻翻动，待蛋液全部凝固、没有流动的生蛋液后关火装盘。")],
        ["孕妇、老人、儿童及免疫力较弱者应食用充分熟制的蛋类。"], "dishes/vegetable_dish/炒滑蛋/炒滑蛋.jpg"),
    recipe("kung-pao", "宫保鸡丁", "鸡丁、花生与葱段，炒出一盘有层次的家常滋味。", "家常菜/肉类", 30, "中等", "原始份量 · 2—3 人份",
        [("鸡腿肉", "300 克"), ("熟花生米", "50 克"), ("大葱", "1 根"), ("干辣椒", "4 个"), ("生抽", "2 汤匙"), ("香醋", "1 汤匙"), ("白糖", "1 茶匙"), ("淀粉", "2 茶匙"), ("食用油", "适量")],
        ["鸡腿肉去骨切丁，用 1 汤匙生抽和 1 茶匙淀粉抓匀。", "剩余生抽、淀粉与香醋、白糖、2 汤匙水混合成碗汁。"],
        [("切好配料", "大葱切成小段，干辣椒剪段并去除部分辣椒籽。备好熟花生米。"),
         ("炒制鸡丁", "锅中放油，倒入鸡丁炒散。持续翻炒，直到鸡丁内部充分熟透，再盛出备用。"),
         ("炒香葱椒", "用少量底油小火炒香干辣椒，放入葱段翻炒。避免将辣椒炒黑。"),
         ("合炒收汁", "倒回鸡丁，碗汁重新搅匀后沿锅边加入。翻炒至酱汁包裹鸡丁，加入熟花生米炒匀出锅。")],
        ["含花生、大豆等过敏原。", "处理生鸡肉的刀具和砧板应与即食食材分开。"], "dishes/meat_dish/宫保鸡丁/宫保鸡丁.jpg"),
    recipe("steamed-fish", "清蒸鲈鱼", "姜葱衬出鱼肉本味，一道清爽的家常蒸菜。", "家常菜/海鲜", 25, "中等", "原始份量 · 2—3 人份",
        [("鲈鱼", "1 条，约 500 克"), ("生姜", "1 块"), ("小葱", "3 根"), ("蒸鱼豉油", "2 汤匙"), ("食用油", "1 汤匙")],
        ["请商家处理鱼鳞、鱼鳃和内脏，回家后冲洗干净并擦干。", "姜切片与细丝，葱切段并留少量葱丝。"],
        [("垫姜摆鱼", "蒸盘铺上姜片和葱段，放上鲈鱼。鱼身厚处浅划两刀，方便均匀受热。"),
         ("上锅清蒸", "蒸锅加足水，水开后放入鱼盘，盖好锅盖蒸制。约 10—15 分钟后开始检查，时间需随鱼的大小调整。"),
         ("检查并换料", "确认鱼肉充分熟制后取出。倒去盘中的蒸汁，移除葱段和姜片，铺上新鲜姜丝与葱丝。", "厚处鱼肉应熟透；有疑问时继续加热，不能仅凭时间保证熟度。"),
         ("淋汁上桌", "沿盘边倒入蒸鱼豉油。将油加热后小心淋在姜葱丝上，注意防止热油飞溅。")],
        ["鱼刺需仔细挑除，尤其注意儿童进食。"], "dishes/aquatic/清蒸鲈鱼/清蒸鲈鱼.jpg"),
    recipe("three-treasures", "地三鲜", "土豆、茄子和青椒，把普通蔬菜做得有滋有味。", "家常菜/素食", 35, "中等", "原始份量 · 2—3 人份",
        [("土豆", "1 个"), ("茄子", "1 根"), ("青椒", "1 个"), ("蒜", "3 瓣"), ("生抽", "1 汤匙"), ("老抽", "半茶匙"), ("淀粉", "1 茶匙"), ("食用油", "适量")],
        ["土豆和茄子切成滚刀块，青椒去籽切块。土豆冲去表面淀粉后擦干。"],
        [("煎熟土豆", "平底锅中放少量油，中小火煎土豆块，间歇翻面，煎至表面金黄、内部熟软后盛出。"),
         ("煎软茄子", "锅中按需补油，放入茄子，保持中小火翻煎至熟软。不要一次加入过多油。"),
         ("炒香青椒", "加入蒜末和青椒块，翻炒至青椒断生，再倒回土豆。"),
         ("调汁合炒", "生抽、老抽、淀粉与半碗水调匀倒入，轻轻翻炒至三种蔬菜充分熟制、酱汁略稠后装盘。")],
        ["土豆发绿、发芽或明显变质时不要食用。"], "dishes/vegetable_dish/地三鲜/地三鲜.jpg"),
    recipe("corn-soup", "玉米排骨汤", "一锅慢慢炖的清汤，有玉米的清甜，也有家的温度。", "家常菜/肉类/汤羹", 90, "简单", "原始份量 · 3—4 人份",
        [("猪排骨", "500 克"), ("甜玉米", "1 根"), ("胡萝卜", "1 根"), ("姜", "4 片"), ("盐", "适量"), ("水", "约 1.5 升")],
        ["排骨切段，玉米切块，胡萝卜去皮切块。"],
        [("排骨焯水", "排骨冷水下锅，煮出浮沫后捞出，用温水洗净。"),
         ("炖煮排骨", "排骨、姜片和水放入汤锅，大火煮开后转小火，盖上锅盖炖约 40 分钟。"),
         ("加入蔬菜", "放入玉米和胡萝卜，继续小火炖约 25—30 分钟。留意水量，避免烧干。"),
         ("调味盛汤", "检查排骨已经熟透、蔬菜软熟后，加盐调味，搅匀盛出。")],
        ["热汤端取时注意防烫；骨头不能替代对熟度的检查。"], "dishes/soup/玉米排骨汤/玉米排骨汤.jpeg"),
    recipe("mapo-tofu", "麻婆豆腐", "豆腐裹着微辣酱汁，拌一碗米饭就很满足。", "家常菜/肉类", None, "中等", "原始份量 · 2 人份",
        [("嫩豆腐", "400 克"), ("猪肉末", "100 克"), ("豆瓣酱", "1 汤匙"), ("蒜", "2 瓣"), ("生抽", "1 茶匙"), ("淀粉", "1 茶匙"), ("小葱", "1 根")], [],
        [("准备豆腐", "豆腐切成方块，放入微沸的水中略煮，轻轻捞出沥水。"),
         ("炒香肉末", "锅中放油，加入肉末炒散并炒至完全熟透。加入蒜末和豆瓣酱，小火炒香。"),
         ("煨入滋味", "加一小碗热水，放入豆腐和生抽，小火煨煮，使豆腐充分加热并入味。"),
         ("薄芡收汁", "淀粉加少量水调匀，分次加入，轻推豆腐至酱汁略稠。撒上葱花出锅。")],
        ["豆瓣酱含盐，调味前先尝咸淡。含大豆过敏原。"], "dishes/meat_dish/麻婆豆腐/1.jpeg"),
    recipe("cauliflower", "干锅花菜", "脆嫩花菜与一点酱香，一盘好吃的下饭蔬菜。", "家常菜/素食", 25, "中等", "原始份量 · 2 人份",
        [("花菜", "400 克"), ("青椒", "1 个"), ("蒜", "3 瓣"), ("干辣椒", "2 个"), ("生抽", "1 汤匙"), ("盐", "少许"), ("食用油", "适量")],
        ["花菜分成小朵，充分清洗后沥干。青椒切块，蒜切片。"],
        [("焯好花菜", "烧开一锅水，放入花菜焯煮至接近熟制，捞出充分沥水。"),
         ("炒香配料", "锅中热油，放入蒜片、干辣椒，以小火炒香。"),
         ("翻炒调味", "放入花菜和青椒翻炒，加入生抽与少许盐。"),
         ("炒熟装盘", "继续翻炒至蔬菜熟透，锅内水分收干一些后盛出。")], [], "dishes/vegetable_dish/干锅花菜/干锅花菜.jpg"),
    recipe("greens", "白灼菜心", "留住青菜的清爽，简单调味也很鲜。", "家常菜/快手菜/素食", 10, "简单", "原始份量 · 2 人份",
        [("菜心", "300 克"), ("蒜", "2 瓣"), ("生抽", "1 汤匙"), ("食用油", "1 汤匙"), ("水", "适量")], [],
        [("清洗菜心", "菜心去除老叶与较硬的根部，逐根清洗，蒜切末。"),
         ("焯熟沥水", "水烧开，加入少量食用油。先放菜梗，再放叶片，焯至熟制后捞出沥水。"),
         ("淋汁装盘", "锅中放油炒香蒜末，加入生抽和两汤匙水煮开，淋在菜心上。")], [], "dishes/vegetable_dish/白灼菜心/白灼菜心.jpg"),
    recipe("cabbage", "手撕包菜", "手撕的叶片挂住酱汁，大火快炒的家常香气。", "家常菜/快手菜/素食", 15, None, "原始份量 · 2 人份",
        [("包菜", "半颗"), ("蒜", "3 瓣"), ("干辣椒", "2 个"), ("生抽", "1 汤匙"), ("香醋", "1 茶匙"), ("食用油", "适量")],
        ["包菜去掉硬梗，叶片手撕成块，洗净后充分沥水。"],
        [("炒香蒜椒", "锅中放油，放入蒜片和干辣椒，小火炒出香味。"),
         ("翻炒包菜", "加入包菜转中大火，不断翻炒，让叶片均匀受热。"),
         ("调味出锅", "待包菜熟制后，加入生抽和香醋，快速翻炒均匀后出锅。")],
        ["叶片沥干后再入锅，避免热油飞溅。"], "dishes/vegetable_dish/手撕包菜/4.jpeg"),
    recipe("shrimp", "油焖大虾", "鲜虾与浓香酱汁，让一餐多一点小小的仪式感。", "家常菜/海鲜", 25, "中等", "原始份量 · 2—3 人份",
        [("鲜虾", "400 克"), ("姜", "3 片"), ("小葱", "2 根"), ("生抽", "1 汤匙"), ("料酒", "1 汤匙"), ("白糖", "半茶匙"), ("食用油", "适量")],
        ["鲜虾剪去长须，按需去虾线，清洗后擦干表面。"],
        [("煎香鲜虾", "锅中热油，放入鲜虾平铺，翻面煎至虾壳变色。此时仍需继续充分加热。"),
         ("加入调料", "放入姜片葱段炒香，加入料酒、生抽、白糖和少量热水。"),
         ("焖煮入味", "盖上锅盖，中小火焖煮，期间翻动，让虾均匀加热。"),
         ("收汁装盘", "确认虾肉完全熟制后开盖收汁，轻翻使酱汁包裹虾身，再盛盘。")],
        ["含甲壳类过敏原。不能仅凭虾壳颜色判断内部熟度。"], "dishes/aquatic/油焖大虾/油焖大虾.jpg"),
    recipe("tomato-tofu", "番茄豆腐汤", "番茄的酸甜与豆腐的柔软，做一碗清爽热汤。", "家常菜/快手菜/素食/汤羹", 15, "简单", "原始份量 · 2 人份",
        [("番茄", "2 个"), ("嫩豆腐", "200 克"), ("小葱", "1 根"), ("盐", "适量"), ("食用油", "少许"), ("水", "600 毫升")], [],
        [("切好食材", "番茄洗净切块，豆腐切小块，小葱切葱花。"),
         ("炒软番茄", "锅中加入少量油，放入番茄中火翻炒，直到番茄变软并出现汤汁。"),
         ("煮好汤底", "加入水煮开，放入豆腐，转小火煮约 5 分钟，让食材充分受热。"),
         ("调味盛出", "加盐调味，撒入葱花，轻推均匀后盛出。")],
        ["嫩豆腐容易碎，搅动时轻推即可。"], "dishes/vegetable_dish/西红柿豆腐汤羹/1.jpeg"),
]

def main():
    if os.environ.get("DEEPCOOK_REBUILD_CONTENT") != "1":
        print("Refusing to run: this script rebuilds only the twelve original recipes and would")
        print("drop the expansion catalog. Set DEEPCOOK_REBUILD_CONTENT=1 to confirm, then")
        print("re-merge the expansions (see docs/菜谱扩展1.md).")
        return
    api = "https://api.github.com/repos/Anduin2017/HowToCook/commits/master"
    commit = json.load(urllib.request.urlopen(api))["sha"]
    raw = f"https://raw.githubusercontent.com/Anduin2017/HowToCook/{commit}/"
    def download(r):
        path = r.pop("_photo")
        url = raw + urllib.parse.quote(path)
        dest = ASSETS / "photos" / f"{r['id']}.jpg"
        try:
            data = urllib.request.urlopen(url, timeout=45).read()
            image = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
            image.thumbnail((1200, 1000))
            image.save(dest, quality=85, optimize=True)
            r["cover"] = f"photos/{r['id']}.jpg"
        except Exception as error:
            print(f"Photo unavailable for {r['id']}: {error}")
            r["cover"] = None
        r["source"]["imageSource"] = f"https://github.com/Anduin2017/HowToCook/blob/{commit}/" + urllib.parse.quote(path)
        return dict(recipeId=r["id"], path=path, url=url, bundled=r["cover"], license="Unlicense", transformation="EXIF orientation, resize to max 1200×1000, JPEG conversion")
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        attribution = list(pool.map(download, RECIPES))
    for r in RECIPES[:6]: r["featured"] = True
    for r in RECIPES:
        r["steps"][0]["ingredientRefs"] = [r["ingredients"][0]["id"]]
    # Real step images for a few recipes; missing images remain genuinely optional.
    for r in RECIPES:
        if r["id"] in ("steamed-fish", "braised-pork", "corn-soup"):
            r["steps"][-1]["image"] = r["cover"]
    (ASSETS / "recipes.json").write_text(json.dumps(RECIPES, ensure_ascii=False, indent=2), encoding="utf-8")
    (ASSETS / "photo_sources.json").write_text(json.dumps(dict(repository="Anduin2017/HowToCook", commit=commit, images=attribution), ensure_ascii=False, indent=2), encoding="utf-8")
    (ASSETS / "HOWTOCOOK-LICENSE.txt").write_bytes(urllib.request.urlopen(raw + "LICENSE").read())
    print(f"Prepared {len(RECIPES)} recipes; {sum(bool(r['cover']) for r in RECIPES)} photographs; commit {commit}")

if __name__ == "__main__": main()
