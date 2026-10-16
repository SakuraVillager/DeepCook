"""Repeat real navigation with animations enabled and collect Android frame timing.

Dedicated emulator only. Installs the specified APK, clears its test data, and
records an initial sample and repeated warm navigation. Software emulator results are
diagnostic comparisons, not physical-device frame-rate guarantees.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def summarize(raw):
    def number(pattern, cast=int):
        match = re.search(pattern, raw, re.MULTILINE)
        return cast(match.group(1)) if match else None
    return dict(
        frames=number(r'^Total frames rendered: (\d+)'),
        janky_frames=number(r'^Janky frames: (\d+)'),
        janky_percent=number(r'^Janky frames: \d+ \(([\d.]+)%\)', float),
        p50_ms=number(r'^50th percentile: (\d+)ms'),
        p90_ms=number(r'^90th percentile: (\d+)ms'),
        p95_ms=number(r'^95th percentile: (\d+)ms'),
        p99_ms=number(r'^99th percentile: (\d+)ms'),
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--apk', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--iterations', type=int, default=6)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Only dedicated emulators are supported; app test data is cleared')
    sdk = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
    adb = [str(sdk / 'platform-tools/adb.exe'), '-s', args.serial]
    def run(*cmd):
        return subprocess.check_output(adb + list(cmd)).decode('utf-8', errors='replace')
    def shell(*cmd):
        return run('shell', *map(str, cmd))
    def point(label):
        shell('uiautomator', 'dump', '/data/local/tmp/deepcook-metrics.xml')
        root = ET.fromstring(shell('cat', '/data/local/tmp/deepcook-metrics.xml'))
        for node in root.iter('node'):
            if label in (node.get('text'), node.get('content-desc')):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                return ((x1 + x2) // 2, (y1 + y2) // 2)
        raise RuntimeError(f'Missing UI element: {label}')
    def tap(p):
        shell('input', 'tap', *p)
        time.sleep(.65)
    def back():
        shell('input', 'keyevent', '4')
        time.sleep(.65)
    # Fresh installs also allow comparing an older versionCode after a newer APK.
    if shell('pm', 'path', 'com.deepcook.app').strip():
        run('uninstall', 'com.deepcook.app')
    run('install', str(Path(args.apk).resolve()))
    shell('pm', 'clear', 'com.deepcook.app')
    for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        shell('settings', 'put', 'global', setting, '1')
    shell('am', 'start', '-W', '-n', 'com.deepcook.app/.MainActivity')
    time.sleep(3)
    home = point('找菜')
    profile = point('我的')
    recipe = point('红烧肉')
    # Read the reader button's position once; its bottom position is stable.
    tap(recipe)
    reader = point('分步阅读')
    back()
    output = Path('artifacts/reports/polish-0.1.2')
    output.mkdir(parents=True, exist_ok=True)
    metrics = {}
    for name, iterations in [('first-sample', 1), ('warm', args.iterations)]:
        shell('dumpsys', 'gfxinfo', 'com.deepcook.app', 'reset')
        for _ in range(iterations):
            tap(profile)
            tap(home)
            tap(recipe)
            tap(reader)
            back()
            back()
        raw = shell('dumpsys', 'gfxinfo', 'com.deepcook.app', 'framestats')
        (output / f'{args.label}-{name}-framestats.txt').write_text(raw, encoding='utf-8')
        metrics[name] = summarize(raw)
    (output / f'{args.label}-summary.json').write_text(json.dumps(metrics, indent=2), encoding='utf-8')
    print(json.dumps(metrics, indent=2))


if __name__ == '__main__':
    main()
