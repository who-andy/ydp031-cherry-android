"""Assemble the downloadable installer using only the versioned file lists."""
import argparse
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from image_parts import checksum

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--platform',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path)
    p.add_argument('--windows-launcher',type=Path,help='Include the locally compiled Windows executable')
    args=p.parse_args()
    files={path.name:path for path in ROOT.iterdir() if path.is_file() and
           (path.suffix in ('.py','.json','.sh','.command') or path.name=='README.md')}
    if args.windows_launcher:
        files['Start-Windows.exe']=args.windows_launcher
    for name in ('docs','build','tests'):
        for path in (ROOT/name).rglob('*'):
            if path.is_file() and not path.is_symlink() and path.suffix in ('.md','.py','.json','.png','.sh','.config','.c','.java','.xml','.txt','.cs','.ps1'):
                files[path.relative_to(ROOT).as_posix()]=path
    for name,h in json.loads((ROOT/'platform-files.json').read_bytes()).items():
        if name=='apps/via-release.apk':
            continue
        path=args.platform/name
        if path.is_symlink() or checksum(path)!=h: raise ValueError('模板校验失败 '+name)
        files['platform/'+name]=path
    with args.output.open('xb') as out:
        try:
            with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=5) as archive:
                for name,path in sorted(files.items()):
                    info=zipfile.ZipInfo('Cherry-YDP031/'+name)
                    info.create_system=3
                    info.external_attr=(0o100755 if name.endswith(('.command','.sh')) else 0o100644)<<16
                    info.compress_type=zipfile.ZIP_DEFLATED
                    archive.writestr(info,path.read_bytes())
        except BaseException:
            args.output.unlink()
            raise
    print('安装包 SHA-256',checksum(args.output))

if __name__=='__main__':
    main()
