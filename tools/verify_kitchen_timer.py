"""Exercise actual timer permissions, process death and reboot on a dedicated emulator only."""
import argparse
import json
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
    parser.add_argument("--reboot", action="store_true")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Only a dedicated Android emulator may be used")
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")))
    adb = [str(sdk / "platform-tools/adb.exe"), "-s", args.serial]
    evidence = {}
    stamp = str(int(time.time()))
    process_label = "ProcessProbe" + stamp
    reboot_label = "RebootProbe" + stamp

    def run(*command, check=True):
        result = subprocess.run(adb + list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check, timeout=60)
        return result.stdout.decode("utf-8", errors="replace")

    def shell(*command, check=True):
        return run("shell", *command, check=check)

    def hierarchy():
        for _ in range(5):
            try:
                shell("uiautomator", "dump", "/data/local/tmp/kitchen-ui.xml")
                return ET.fromstring(shell("cat", "/data/local/tmp/kitchen-ui.xml"))
            except (ET.ParseError, subprocess.CalledProcessError):
                time.sleep(1)
        raise RuntimeError("UI hierarchy unavailable")

    def click_node(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
        time.sleep(.5)

    def tap(label):
        for _ in range(5):
            nodes = [n for n in hierarchy().iter("node") if label in (n.get("text"), n.get("content-desc"))]
            if nodes:
                click_node(sorted(nodes, key=lambda n: n.get("clickable") != "true")[0])
                return
            time.sleep(.5)
        raise RuntimeError(f"UI action not found: {label}")

    def capture(name):
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / f"{name}.png").write_bytes(subprocess.check_output(adb + ["exec-out", "screencap", "-p"]))
        print(name, flush=True)

    def set_field(index, value):
        fields = [n for n in hierarchy().iter("node") if n.get("class") == "android.widget.EditText"]
        field = fields[index]
        click_node(field)
        shell("input", "keyevent", "123", *(["67"] * (len(field.get("text", "")) + 2)))
        shell("input", "text", value)

    def prepare_timer(label, seconds):
        tap("添加计时器")
        set_field(0, label)
        set_field(1, "0")
        set_field(2, str(seconds//60))
        set_field(3, str(seconds % 60))
        tap("开始计时")

    def wait_alarm(label, timeout):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            dump = shell("dumpsys", "notification", "--noredact")
            if f"{label} · 时间到" in dump:
                evidence[label] = {"alarm_received": True, "pid": shell("pidof", "com.deepcook.app", check=False).strip()}
                (ROOT / f"artifacts/{label}-notifications.txt").write_text(dump, encoding="utf-8")
                return
            time.sleep(2)
        raise AssertionError(f"No deadline notification after {label}")

    def stop_alarm():
        shell("am", "start", "-W", "-n", "com.deepcook.app/.MainActivity", "--es", "timer_recipe", "braised-pork")
        time.sleep(2)
        tap("停止提醒")

    shell("pm", "revoke", "com.deepcook.app", "android.permission.POST_NOTIFICATIONS", check=False)
    shell("appops", "set", "com.deepcook.app", "SCHEDULE_EXACT_ALARM", "deny")
    shell("am", "start", "-W", "-n", "com.deepcook.app/.MainActivity")
    time.sleep(2)
    capture("kitchen-feature-home-counts")
    tap("红烧肉")
    capture("kitchen-rating-overview")
    tap("烹饪模式")
    capture("kitchen-step-timer-suggestions")
    tap("计时器")
    prepare_timer(process_label, 40)
    capture("kitchen-timer-permissions")
    assert any(n.get("text") == "启用计时提醒" for n in hierarchy().iter("node"))
    tap("开启通知")
    capture("kitchen-notification-system-permission")
    shell("pm", "grant", "com.deepcook.app", "android.permission.POST_NOTIFICATIONS")
    time.sleep(1)
    # A grant made by adb while the system dialog is showing can leave that dialog visible.
    nodes = list(hierarchy().iter("node"))
    for n in nodes:
        if n.get("text") in ("Allow", "允许"):
            click_node(n)
            break
    tap("允许精确闹钟")
    capture("kitchen-exact-alarm-system-permission")
    shell("appops", "set", "com.deepcook.app", "SCHEDULE_EXACT_ALARM", "allow")
    shell("input", "keyevent", "4")
    time.sleep(1)
    tap("开始计时")
    capture("kitchen-timer-running")
    old_pid = shell("pidof", "com.deepcook.app").strip()
    shell("input", "keyevent", "3")
    time.sleep(2)
    shell("am", "kill", "com.deepcook.app")
    time.sleep(1)
    assert not shell("pidof", "com.deepcook.app", check=False).strip(), "The background process did not exit"
    evidence["process_killed"] = old_pid
    print("Background process exited; waiting for system alarm", flush=True)
    wait_alarm(process_label, 55)
    shell("cmd", "statusbar", "expand-notifications")
    time.sleep(1)
    capture("kitchen-alarm-after-process-death")
    shell("cmd", "statusbar", "collapse")
    stop_alarm()

    if args.reboot:
        prepare_timer(reboot_label, 90)
        capture("kitchen-timer-before-reboot")
        boot = shell("settings", "get", "global", "boot_count").strip()
        run("reboot")
        run("wait-for-device")
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            if shell("getprop", "sys.boot_completed", check=False).strip() == "1":
                break
            time.sleep(2)
        assert shell("getprop", "sys.boot_completed").strip() == "1"
        assert shell("settings", "get", "global", "boot_count").strip() != boot
        shell("input", "keyevent", "82")
        print("Emulator rebooted; waiting for restored alarm", flush=True)
        wait_alarm(reboot_label, 110)
        shell("cmd", "statusbar", "expand-notifications")
        time.sleep(1)
        capture("kitchen-alarm-after-reboot")
        shell("cmd", "statusbar", "collapse")
        stop_alarm()
    (ROOT / "artifacts/kitchen-timer-recovery.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
