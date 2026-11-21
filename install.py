#!/usr/bin/env python3
"""有道词典笔 YDP031CHNSTD 3.4.6 双系统安装器。"""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

from disk import BLOCK,DiskFile,Loader,META,PARTS,RANGES,SIZE,digest,identity,pieces,region_hash,require,trial

def new_output(path):
    require(not path.exists() and not path.is_symlink(),'输出路径已经存在')
    path.parent.mkdir(parents=True,exist_ok=True)

def backup(args):
    new_output(args.output)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as ts:
        tmp=Path(ts)
        device=Loader(args.loader,tmp)
        ident=identity(device)
        require(ident['serial']==args.serial,'连接设备序列号不匹配')
        file=tmp/'backup.img'
        h=hashlib.sha256()
        with file.open('xb') as out:
            for pos,n in pieces(0,SIZE):
                data=device.read(pos,n)
                h.update(data)
                if data.count(0)==len(data):out.seek(n,1)
                else:out.write(data)
            out.truncate(SIZE)
            out.flush()
            os.fsync(out.fileno())
        checksum=h.hexdigest()
        require(region_hash(DiskFile(file),0,SIZE)==checksum==region_hash(device,0,SIZE),'备份与设备复读校验不一致')
        os.link(file,args.output)
        print('备份已回读，SHA-256',checksum)

def prepare(args):
    require(sys.platform=='linux' and os.getuid()==0,'prepare 需要 Linux 管理员权限，用于挂载备份副本')
    new_output(args.output)
    # Confine the loop mounts even on failure or interruption.
    subprocess.run(['unshare','--mount','--propagation','private',sys.executable,__file__,'_prepare',
                    '--backup',args.backup,'--platform',args.platform,'--output',args.output],check=True)

def stage_private(args):
    from prepare import stage
    new_output(args.output)
    require(os.getuid()==0,'prepare 需要 Linux 管理员权限')
    with tempfile.TemporaryDirectory(dir=args.output.parent) as ts:
        tmp=Path(ts).resolve()
        result=tmp/'package'
        result.mkdir(mode=0o700)
        stage(args,tmp,result)
        # The user running sudo must be able to carry this private bundle back to the USB host.
        if 'SUDO_UID' in os.environ and 'SUDO_GID' in os.environ:
            uid,gid=int(os.environ['SUDO_UID']),int(os.environ['SUDO_GID'])
            for path in result.rglob('*'):
                os.chown(path,uid,gid,follow_symlinks=False)
            os.chown(result,uid,gid)
        new_output(args.output)
        result.rename(args.output)

def read_plan(directory,source,ident):
    raw=(directory/'update.json').read_bytes()
    require(digest(raw)==(directory/'update.sha256').read_text().strip(),'安装清单校验失败')
    p=json.loads(raw)
    require(p['format']==1 and p['device']==ident and p['backup_sha256']==region_hash(source,0,SIZE),'安装文件与所选备份不匹配')
    rows={}
    allowed={off:n for start,size in RANGES for off,n in pieces(start,size)}
    for pos,n,old,new in p['changes']:
        require(allowed.get(pos)==n and pos not in rows and
                all(isinstance(h,str) and re.fullmatch('[0-9a-f]{64}',h) for h in (old,new)),'安装块越界、重复或校验格式无效')
        block=directory/'blocks'/new
        require(block.is_file() and not block.is_symlink() and block.stat().st_size==n and digest(block.read_bytes())==new,'安装块损坏')
        require(digest(source.read(pos,n))==old,'安装差异与备份不一致')
        rows[pos]=(old,new)
    require(META in rows and RANGES[1][0] in rows and any(pos>=RANGES[0][0] for pos in rows),'安装计划缺少必要分区')
    require((directory/'blocks'/rows[META][1]).read_bytes()==trial(source.read(META,512)),'启动选择与回退策略不匹配')
    return rows

def transfer(args):
    restore=args.action=='restore'
    source=DiskFile(args.backup)
    ident=identity(source)
    require(args.confirm_serial==ident['serial'],'确认序列号与备份不符')
    if restore and args.backup_sha256:
        require(re.fullmatch('[0-9a-f]{64}',args.backup_sha256) and region_hash(source,0,SIZE)==args.backup_sha256,
                '恢复用备份的 SHA-256 不符')
        trial(source.read(META,512))
        changed={}
    else:
        require(args.package is not None,'需要 --package，独立恢复也可使用 --backup-sha256')
        changed=read_plan(args.package,source,ident)
    with tempfile.TemporaryDirectory() as td,contextlib.ExitStack() as stack:
        # Serialize all operations for this device before preflight or writes.
        from tempfile import gettempdir
        lockdir=Path(gettempdir())/('cherry-installer-'+str(os.getuid()))
        lockdir.mkdir(mode=0o700,exist_ok=True)
        require(not lockdir.is_symlink() and lockdir.stat().st_uid==os.getuid() and lockdir.stat().st_mode&0o077==0,'锁目录权限错误')
        lock=stack.enter_context((lockdir/ident['serial']).open('a+b'))
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.disk_copy:
            require(not os.path.samefile(args.backup,args.disk_copy),'目标与备份是同一个文件')
            target=DiskFile(args.disk_copy,'r+b')
            stack.callback(target.file.close)
            fcntl.flock(target.file,fcntl.LOCK_EX|fcntl.LOCK_NB)
        else:target=Loader(args.loader,Path(td))
        require(identity(target)==ident,'目标设备身份或 GPT 与备份不符')
        # Scan all bytes in the write scope before any mutation. Install and
        # interrupted install accept only baseline or the exact candidate.
        if not restore:
            for start,length in RANGES:
                for off,n in pieces(start,length):
                    old=source.read(off,n)
                    data=target.read(off,n)
                    if data!=old:
                        require(off in changed,'目标内容已变化，请重新备份：'+str(off))
                        if digest(data)!=changed[off][1]:
                            candidate=(args.package/'blocks'/changed[off][1]).read_bytes()
                            # A disconnected transfer can leave complete sectors from either version.
                            require(all(data[i:i+512] in (old[i:i+512],candidate[i:i+512])
                                        for i in range(0,n,512)),'目标内容已变化，请重新备份：'+str(off))
            for k in ('system_a','system_b'):
                pos,n=PARTS[k]
                require(region_hash(target,pos*512,n*512)==region_hash(source,pos*512,n*512),'原厂系统与备份不符 '+k)
        if args.action=='check':
            print('设备、原厂回退、备份、写入范围和差异块已核验。')
            return
        # Boot selection is always last. Reapply after interruption is idempotent.
        for start,length in RANGES:
            for off,n in pieces(start,length):
                if not restore and off not in changed:continue
                new=source.read(off,n) if restore else (args.package/'blocks'/changed[off][1]).read_bytes()
                if not restore:require(digest(new)==changed[off][1],'安装文件在执行中发生变化')
                if target.read(off,n)!=new:
                    target.write(off,new)
                    require(target.read(off,n)==new,'写入回读失败：'+str(off))
        print('已恢复备份中的用户分区、启动 A 与引导记录。' if restore else '安装写入与回读完成。')
        if args.loader:print('设备仍在 Loader。使用相同的 Rockchip 工具执行 rd 可重启。')

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__)
    subs=p.add_subparsers(dest='action',required=True)
    s=subs.add_parser('verify-platform',help='核对本地平台材料')
    s.add_argument('--platform',required=True,type=Path)
    s=subs.add_parser('inspect',help='只读识别备份')
    s.add_argument('--backup',required=True,type=Path)
    s=subs.add_parser('backup',help='从已进入 Loader 的设备完整备份 eMMC 用户区')
    s.add_argument('--loader',required=True);s.add_argument('--serial',required=True)
    s.add_argument('--output',required=True,type=Path)
    for action in ('prepare','_prepare'):
        s=subs.add_parser(action)
        s.add_argument('--backup',required=True,type=Path)
        s.add_argument('--platform',required=True,type=Path)
        s.add_argument('--output',required=True,type=Path)
    for action in ('check','install','restore'):
        s=subs.add_parser(action)
        s.add_argument('--backup',required=True,type=Path)
        if action=='restore':
            source_group=s.add_mutually_exclusive_group(required=True)
            source_group.add_argument('--package',type=Path)
            source_group.add_argument('--backup-sha256',help='备份时记录的 SHA-256，可独立恢复')
        else:s.add_argument('--package',required=True,type=Path)
        s.add_argument('--confirm-serial',required=True)
        g=s.add_mutually_exclusive_group(required=True)
        g.add_argument('--disk-copy',type=Path,help='写入可丢弃的普通镜像副本')
        g.add_argument('--loader',help='Rockchip 维护工具路径')
    args=p.parse_args()
    if args.action=='verify-platform':
        from prepare import verify_platform
        print('平台组件校验通过，文件数',len(verify_platform(args.platform.resolve(strict=True))))
    elif args.action=='inspect':
        src=DiskFile(args.backup)
        print(json.dumps(identity(src),ensure_ascii=False))
    elif args.action=='backup':backup(args)
    elif args.action in ('prepare','_prepare'):
        args.platform=args.platform.resolve(strict=True)
        if args.action=='prepare':prepare(args)
        else:stage_private(args)
    else:transfer(args)

if __name__=='__main__':
    try:main()
    except (ValueError,OSError,KeyError,subprocess.CalledProcessError) as e:
        sys.exit('安装器：'+str(e))
