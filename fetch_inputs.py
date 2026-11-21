"""Download pinned third-party inputs directly from their publishers."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from image_parts import checksum

SDK = 'https://dl.google.com/android/repository/sys-img/android/arm64-v8a-27_r02.zip'
MALI = 'https://gitlab.com/rockchip_android_r/rk/platform/vendor/rockchip/common/-/raw/126be84e8d9db7c0b9cebed52dbfe376111da205/gpu/MaliTDVx/lib/arm64/libGLES_mali.so'
VIA = 'https://res.viayoo.com/v1/via-release.apk'
SDK_SHA = '5e079c20e557c6e0191932aea2e832a340b7ed66fbfa8b87f6141517cc73992c'
VIA_SHA = 'ed7d947fb9fa787b43d16ee85edef655799ecedacf510673f0c7417ca5b9d03c'

def download(url, path, expected):
    subprocess.run(['curl', '--fail', '--location', '--retry', '3', '--connect-timeout', '20',
                    '--output', str(path), url], check=True)
    if checksum(path) != expected:
        raise ValueError('上游下载内容与此版不匹配 ' + path.name)

def fetch(platform):
    folder = platform / 'upstream'
    folder.mkdir(exist_ok=True)
    specs = [json.loads(p.read_bytes()) for p in sorted((platform/'images').glob('*.json'))]
    expected = {s['file']: s['sha256'] for spec in specs for s in spec['sources'] if 'file' in s}
    if set(expected) != {'system.img','vendor.img','mali.so'}:
        raise ValueError('下载包中的镜像清单不完整')
    todo = {k for k,v in expected.items() if not (folder/k).is_file() or checksum(folder/k) != v}
    with tempfile.TemporaryDirectory(dir=folder) as td:
        tmp = Path(td)
        if todo & {'system.img','vendor.img'}:
            download(SDK, tmp/'android.zip', SDK_SHA)
            with zipfile.ZipFile(tmp/'android.zip') as archive:
                for name in sorted(todo & {'system.img','vendor.img'}):
                    with archive.open('arm64-v8a/'+name) as src, (tmp/name).open('xb') as dst:
                        shutil.copyfileobj(src, dst, 1024*1024)
                    if checksum(tmp/name) != expected[name]:
                        raise ValueError('Android 原始文件校验失败')
                    os.replace(tmp/name, folder/name)
        if 'mali.so' in todo:
            download(MALI, tmp/'mali.so', expected['mali.so'])
            os.replace(tmp/'mali.so',folder/'mali.so')
        browser=platform/'apps/via-release.apk'
        if not browser.is_file() or checksum(browser) != VIA_SHA:
            download(VIA, tmp/'via-release.apk', VIA_SHA)
            os.replace(tmp/'via-release.apk', browser)
