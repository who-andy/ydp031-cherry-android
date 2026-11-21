"""Interactive first installation and independent restore for Cherry Android."""
import argparse
import fcntl
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import time
import tempfile
from fetch_inputs import fetch
from image_parts import checksum
from prepare import verify_platform
from disk import require

HERE = Path(__file__).resolve().parent
ROOT = Path.home() / 'CherryPen'
MAC = sys.platform == 'darwin'

def run(*args, capture=False):
    result = subprocess.run(list(map(str,args)), check=True, text=True, stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None

def bundle_check(folder):
    spec=json.loads((folder/'platform-files.json').read_text())
    for name, expected in spec.items():
        if name == 'apps/via-release.apk':
            continue
        path=folder/'platform'/name
        require(path.is_file() and not path.is_symlink() and checksum(path)==expected, '附件缺少或损坏 '+name)
    print('安装包自检通过。', flush=True)

def installed_copy():
    destination = ROOT / 'installer-rc2'
    if HERE == destination:
        return destination
    if not destination.exists():
        with tempfile.TemporaryDirectory(dir=ROOT) as td:
            staging=Path(td)/'installer'
            staging.mkdir()
            for name in ('install.py','prepare.py','disk.py','boot.py','image_parts.py','factory_button.py',
                         'host-tools.sh','platform-files.json','factory-files.json','apps.json','board-modes.json'):
                shutil.copy2(HERE/name,staging/name)
            for name in json.loads((HERE/'platform-files.json').read_text()):
                if name=='apps/via-release.apk':
                    continue
                dest=staging/'platform'/name
                dest.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(HERE/'platform'/name,dest)
            staging.rename(destination)
    # A resumed run must use exactly the release that was launched.
    require((HERE/'platform-files.json').read_bytes()==(destination/'platform-files.json').read_bytes(),
            '工作目录内已有不同版本，请保留备份并使用与它对应的安装器')
    bundle_check(destination)
    return destination

def windows_bridge_serial(output):
    match=re.fullmatch(r'CHERRY_SERIAL=([A-Za-z0-9]{16}|)',output.strip())
    require(match is not None,'Windows USB 助手返回了无效的设备标识')
    return match.group(1)

def work_space(windows_exe):
    available=shutil.disk_usage(ROOT).free
    if windows_exe:
        host=int(run(windows_exe,'--free-bytes',capture=True).strip())
        available=min(available,host)
    return available

def main():
    p=argparse.ArgumentParser(description='有道词典笔 3 双系统安装器')
    group=p.add_mutually_exclusive_group()
    group.add_argument('--self-test',action='store_true',help='只检查下载包，无需连接设备')
    group.add_argument('--download-only',action='store_true',help='下载并核对上游文件，无需连接设备')
    group.add_argument('--windows-action',choices=('install','restore','download'),help=argparse.SUPPRESS)
    args=p.parse_args()
    windows_exe=None
    if args.windows_action:
        import platform
        require('wsl2' in platform.release().lower(),'Windows 入口需要 WSL 2')
        windows_exe=HERE/'Start-Windows.exe'
        require(windows_exe.is_file(),'请从完整的 Windows 下载包启动')
    os.umask(0o077)
    print('Cherry Android · 有道词典笔 3 双系统\n支持 YDP031CHNSTD、16 GB、原厂 B 槽 OS 3.4.6。',flush=True)
    bundle_check(HERE)
    if args.self_test:
        return
    automatic={'install':'1','restore':'3','download':'2'}.get(args.windows_action)
    action=automatic or ('2' if args.download_only else input('1 安装双系统   2 只准备下载   3 恢复自己的原厂备份   回车退出\n请选择：').strip())
    if action not in ('1','2','3'):
        return
    ROOT.mkdir(mode=0o700,exist_ok=True)
    with (ROOT/'installer.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if windows_exe and action=='1' and not any((ROOT/'private').glob('*/stock.img')):
            require(work_space(windows_exe)>=50*1024**3,'首次安装请在 Ubuntu 所在 Windows 磁盘留出至少 50 GiB')
        folder=installed_copy()
        platform=folder/'platform'
        if action!='3':
            print('正在从官方地址下载 Android 基础文件、图形库和浏览器。已通过校验的文件将复用。',flush=True)
            fetch(platform)
            verify_platform(platform.resolve())
            if action=='2':
                print('全部下载并校验完成，尚未访问 USB。')
                return
        require(MAC or sys.platform.startswith('linux'), '请使用 Apple Silicon Mac 或 Ubuntu')
        if MAC:
            import platform as py_platform
            require(py_platform.machine()=='arm64','此版在 Apple Silicon Mac 上验证')

        print('正在准备 USB 工具和 Linux 镜像制作环境。',flush=True)
        run('sh',folder/'host-tools.sh',ROOT)
        tool=ROOT/'tools/rkdeveloptool-304f073'
        prefix=[] if MAC or os.getuid()==0 else ['sudo']
        expected=''
        if windows_exe:
            expected=windows_bridge_serial(run(windows_exe,'--connect-loader',capture=True))
        else:
            rows=[s.split() for s in run('adb','devices',capture=True).splitlines() if s.strip() and not s.startswith('List of devices')]
            if rows:
                require(len(rows)==1 and len(rows[0])==2 and rows[0][1]=='device','请只连接一支笔，并在原厂系统确认 USB 调试授权')
                expected=rows[0][0]
                print('将让这支笔进入 USB 维护模式：',expected,flush=True)
                run('adb','-s',expected,'reboot','bootloader')
        for attempt in range(45):
            report=run(*prefix,tool,'ld',capture=True)
            if 'Loader' in report: break
            time.sleep(1)
        identity=json.loads(run(*prefix,sys.executable,folder/'install.py','identify','--loader',tool,capture=True))
        serial=identity['serial']
        if expected: require(expected==serial,'维护模式设备与刚才的笔不同')
        private=ROOT/'private'/serial
        private.mkdir(mode=0o700,parents=True,exist_ok=True)
        backup=private/'stock.img'; receipt=private/'stock.img.sha256'; update=private/'update-rc2'
        cli=[sys.executable,folder/'install.py']
        if action=='1' and not update.exists():
            needed=(15 if backup.is_file() else 40)*1024**3
            require(work_space(windows_exe)>=needed,'本地空间不足，请保留备份，在个人文件夹所在磁盘腾出空间后重试')
        if action=='1' and not backup.exists():
            print('正在保存整笔备份，随后会从设备再读一遍。请保持连接。',flush=True)
            run(*prefix,*cli,'backup','--loader',tool,'--serial',serial,'--output',backup)
        require(backup.is_file() and receipt.is_file(),'需要这支笔的完整备份和同目录的 stock.img.sha256')
        if prefix: run(*prefix,'chown',f'{os.getuid()}:{os.getgid()}',backup,receipt)
        recorded=receipt.read_text().strip()
        require(checksum(backup)==recorded,'备份与最初记录的校验值不匹配')
        print('已核对备份：',backup,flush=True)
        target=['--loader',tool,'--backup',backup,'--confirm-serial',serial]
        if action=='3':
            if input('恢复将覆盖备份之后新增的数据。输入设备序列号 '+serial+' 确认恢复：').strip()!=serial: return
            run(*prefix,*cli,'restore',*target,'--backup-sha256',recorded)
        else:
            if not update.exists():
                host=(['limactl','shell','--workdir',folder,'cherry-pen-rc2','sudo','python3'] if MAC
                      else [*prefix,sys.executable])
                run(*host,folder/'install.py','prepare','--platform',platform,'--backup',backup,'--output',update)
            print('准备完成。安装会修改用户分区和启动 A，并保留原厂系统。\n输入序列号后，安装器先核对所有写入范围，再开始写入。',flush=True)
            if input('请输入 '+serial+' 确认安装：').strip()!=serial: return
            run(*prefix,*cli,'install',*target,'--package',update)
        run(*prefix,tool,'rd')
        print('写入及回读完成，设备已收到重启指令。请观察实际开机画面。')

if __name__=='__main__':
    try:
        main()
    except (ValueError,OSError,subprocess.CalledProcessError,KeyboardInterrupt,EOFError) as e:
        sys.exit('安装器已停止。'+str(e))
