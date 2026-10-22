"""Capture the actual Android UI on a dedicated emulator (never a physical phone).

Usage: python tools/capture_emulator.py --serial emulator-5554 --reset-user-data
Requires an installed DeepCook APK and adb on PATH or ANDROID_HOME.
Screenshots go to ignored artifacts/screenshots. The scenario exercises sample
favorites and theme settings. Emulator display settings are restored in finally.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/screenshots"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--reset-user-data", action="store_true", help="Reset DeepCook data on the dedicated emulator first")
    parser.add_argument("--detail-only", action="store_true", help="Capture home, detail, reader and large-font home")
    parser.add_argument("--home-only", action="store_true", help="Capture home, favorite selection and large-font home")
    parser.add_argument("--catalog-only", action="store_true", help="Capture imported catalog, missing metadata and translated recipe")
    parser.add_argument("--authoring-only", action="store_true", help="Capture kitchen and local prefill without saving a personal recipe")
    parser.add_argument("--kitchen-only", action="store_true", help="Capture nutrition, ratings, local posts, photo picker and timer UI")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("This tool only operates on a dedicated Android emulator")
    sdk = os.environ.get("ANDROID_HOME", str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    adb = str(Path(sdk) / "platform-tools/adb.exe")
    prefix = [adb, "-s", args.serial]
    def shell(*command):
        return subprocess.check_output(prefix + ["shell", *map(str, command)]).decode("utf-8", errors="replace")
    def capture(name):
        time.sleep(.8)
        hierarchy()
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / f"{name}.png").write_bytes(subprocess.check_output(prefix + ["exec-out", "screencap", "-p"]))
        print(name, flush=True)
    def hierarchy():
        for attempt in range(5):
            try:
                shell("uiautomator", "dump", "/data/local/tmp/deepcook-ui.xml")
                return ET.fromstring(shell("cat", "/data/local/tmp/deepcook-ui.xml"))
            except (subprocess.CalledProcessError, ET.ParseError):
                time.sleep(.7)
        raise RuntimeError("Android UI did not become ready")
    def tap(label):
        root = hierarchy()
        candidates = [node for node in root.iter("node") if label in (node.get("text"), node.get("content-desc"))]
        for node in sorted(candidates, key=lambda n: n.get("clickable") != "true"):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
            time.sleep(.5)
            return
        raise RuntimeError(f"UI element not found: {label}")
    def back(): shell("input", "keyevent", "4"); time.sleep(.5)
    try:
        if args.reset_user_data:
            shell("pm", "clear", "com.deepcook.app")
        shell("am", "force-stop", "com.deepcook.app")
        shell("am", "start", "-W", "-n", "com.deepcook.app/.MainActivity")
        capture("home-412")
        if args.kitchen_only:
            def reveal(label):
                for _ in range(12):
                    if any(label in (node.get("text"), node.get("content-desc")) for node in hierarchy().iter("node")):
                        return
                    shell("input", "swipe", "550", "1750", "550", "800", "350")
                raise RuntimeError(f"Could not reveal {label}")
            tap("红烧肉")
            capture("kitchen-final-rating")
            reveal("每份营养")
            capture("kitchen-nutrition")
            tap("查看全部")
            capture("kitchen-nutrition-all")
            back()
            tap("来源")
            reveal("厨房交流")
            capture("kitchen-community")
            reveal("写评论")
            tap("写评论")
            capture("kitchen-comment-editor")
            tap("取消")
            tap("交作业")
            field = next(node for node in hierarchy().iter("node") if node.get("class") == "android.widget.EditText")
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", field.get("bounds")))
            shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
            shell("input", "text", "Local%scooking%snotes")
            tap("选择成品照片（可选）")
            capture("kitchen-photo-picker")
            selected = False
            for _ in range(6):
                root = hierarchy()
                thumbnails = [n for n in root.iter("node") if n.get("resource-id", "").endswith("/icon_thumbnail")]
                if thumbnails:
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", thumbnails[0].get("bounds")))
                    shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
                    selected = True
                    break
                time.sleep(1)
            if selected:
                time.sleep(1)
                capture("kitchen-submission-photo")
                tap("发布")
                time.sleep(1)
                capture("kitchen-submission-saved")
            else:
                back()
                tap("取消")
                print("No media thumbnail found; picker inspected without saving", flush=True)
            tap("烹饪模式")
            capture("kitchen-final-step-timers")
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("kitchen-step-font-200")
            shell("settings", "put", "system", "font_scale", "1.0")
            back(); back()
            tap("红烧肉")
            shell("settings", "put", "system", "font_scale", "2.0")
            reveal("菜谱评分")
            capture("kitchen-rating-font-200")
            shell("settings", "put", "system", "font_scale", "1.0")
            back()
            tap("我的"); tap("设置"); tap("外观"); tap("深色")
            back(); back(); tap("找菜"); tap("红烧肉")
            capture("kitchen-rating-dark")
            reveal("每份营养")
            capture("kitchen-nutrition-dark")
            return
        if args.authoring_only:
            tap("我的")
            capture("merge-kitchen")
            tap("我的菜谱")
            capture("merge-my-recipes")
            tap("添加菜谱")
            shell("input", "text", "miso")
            shell("input", "keyevent", "66")
            time.sleep(1)
            capture("merge-editor-prefill")
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("merge-editor-font-200")
            back()
            tap("放弃")
            return
        if args.catalog_only:
            tap("搜索菜谱")
            capture("catalog-all")
            root = hierarchy()
            field = next(node for node in root.iter("node") if node.get("class") == "android.widget.EditText")
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", field.get("bounds")))
            shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
            shell("input", "text", "miso")
            time.sleep(1)
            shell("input", "keyevent", "66")
            capture("catalog-international-search")
            tap("日式味噌汤")
            capture("catalog-missing-parameters")
            tap("材料")
            capture("catalog-chinese-ingredients")
            tap("烹饪模式")
            capture("catalog-chinese-reader")
            return
        if args.home_only:
            tap("收藏菜谱")
            capture("home-favorite-selected")
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("home-font-200")
            tap("红烧肉")
            capture("counter-detail-font-200")
            shell("settings", "put", "system", "font_scale", "1.0")
            capture("counter-detail-standard")
            return
        tap("搜索菜谱")
        capture("search-inline")
        root = hierarchy()
        field = next(node for node in root.iter("node") if node.get("class") == "android.widget.EditText")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", field.get("bounds")))
        shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
        shell("input", "text", "miso")
        capture("search-inline-typing")
        shell("input", "keyevent", "66")
        capture("search-inline-results")
        tap("清空关键词")
        back()
        tap("红烧肉")
        capture("recipe-detail")
        tap("收藏菜谱")
        tap("步骤")
        capture("recipe-steps")
        shell("settings", "put", "system", "font_scale", "2.0")
        tap("步骤")
        capture("recipe-steps-font-200")
        shell("settings", "put", "system", "font_scale", "1.0")
        tap("材料")
        capture("recipe-ingredients")
        tap("烹饪模式")
        capture("reader")
        if args.detail_only:
            back(); back()
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("home-font-200")
            return
        back(); back()
        tap("收藏")
        capture("favorites")
        tap("我的")
        capture("kitchen")
        tap("我的菜谱")
        capture("my-recipes")
        back()
        tap("查看全部")
        capture("recent")
        back()
        tap("设置")
        capture("settings")
        tap("阅读")
        capture("reading-settings")
        back()
        tap("本地数据")
        capture("data-settings")
        back()
        tap("内容与来源")
        capture("content-sources")
        tap("菜谱与配图来源清单")
        capture("photo-sources")
        back(); back()
        tap("关于 DeepCook")
        capture("about")
        tap("开源许可")
        capture("licenses")
        back(); back()
        tap("外观")
        capture("appearance")
        tap("深色")
        capture("appearance-dark")
        back(); back()
        tap("找菜")
        capture("home-dark")
        tap("我的"); tap("设置"); tap("外观"); tap("浅色")
        back(); back(); tap("找菜")
        shell("wm", "density", "480")
        capture("home-360")
        tap("搜索菜谱")
        capture("search-inline-360")
        back()
        shell("settings", "put", "system", "font_scale", "1.3")
        capture("home-font-130")
        shell("settings", "put", "system", "font_scale", "2.0")
        capture("home-font-200")
        tap("搜索菜谱")
        capture("search-font-200")
        back()
        tap("我的"); tap("设置")
        capture("settings-font-200")
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("am", "force-stop", "com.deepcook.app")
        shell("am", "start", "-W", "-n", "com.deepcook.app/.MainActivity")
        shell("wm", "size", "1800x1200")
        shell("wm", "density", "320")
        capture("home-tablet-900")
    finally:
        shell("wm", "size", "reset")
        shell("wm", "density", "reset")
        shell("settings", "put", "system", "font_scale", "1.0")

if __name__ == "__main__": main()
