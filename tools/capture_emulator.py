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
        if args.home_only:
            tap("收藏菜谱")
            capture("home-favorite-selected")
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("home-font-200")
            return
        tap("搜索菜谱")
        capture("search-compact")
        back()
        tap("红烧肉")
        capture("recipe-detail")
        tap("收藏菜谱")
        tap("步骤")
        capture("recipe-steps")
        tap("材料")
        capture("recipe-ingredients")
        tap("分步阅读")
        capture("reader")
        if args.detail_only:
            back(); back()
            shell("settings", "put", "system", "font_scale", "2.0")
            capture("home-font-200")
            return
        back(); back()
        tap("我的")
        capture("kitchen")
        tap("设置")
        capture("settings")
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
