"""Build device-bound update blocks from read-only stock filesystems."""
import contextlib
import gzip
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile

from image_parts import materialize
from boot import assemble,personalize
from disk import DiskFile,PARTS,RANGES,SIZE,STOCK_BOOT,digest,identity,pieces,region_hash,require,trial
from factory_button import install as register_button

HERE=Path(__file__).resolve().parent

def run(*args):
    subprocess.run(args,check=True)

def filehash(path):
    import hashlib
    h=hashlib.sha256()
    with path.open('rb') as f:
        while block:=f.read(2**20):
            h.update(block)
    return h.hexdigest()

@contextlib.contextmanager
def mounted(image,point,part=None,ro=True):
    point.mkdir()
    options=['loop','nosuid','nodev','noexec']
    if ro:options.extend(['ro','noload'])
    if part:
        start,count=PARTS[part]
        options.extend(['offset='+str(start*512),'sizelimit='+str(count*512)])
    run('mount','-t','ext4','-o',','.join(options),image,point)
    try:yield point
    finally:run('umount',point)

def read_stock(row,roots,key):
    # The stock launcher can put the same dictionary release in either app slot.
    prefix='board/original-ui/base/lookup-package/'
    if key.startswith(prefix):
        for slot in ('a','b'):
            p=roots['user']/('miniapp/data/mini_app/pkg/8080252464522508/'+slot)/key[len(prefix):]
            if p.resolve().is_relative_to(roots['user']) and p.is_file() and not p.is_symlink() and p.stat().st_size==row['bytes']:
                data=p.read_bytes()
                if digest(data)==row['sha256']:return data
    expected=row.get('source_sha256',row['sha256'])
    for label,relative in row['sources']:
        variants=[relative]
        if '/8080252464522508/b/' in relative:
            variants.append(relative.replace('/8080252464522508/b/','/8080252464522508/a/'))
        for path in variants:
            root=roots[label]
            p=root/path
            if not p.resolve().is_relative_to(root) or not p.is_file() or p.is_symlink():continue
            if 'source_sha256' not in row and p.stat().st_size!=row['bytes']:continue
            content=p.read_bytes()
            if digest(content)!=expected:continue
            if 'edits' in row:
                data=bytearray(content)
                for index,before,after in row['edits']:
                    require(data[index]==before,'词典版本不匹配')
                    data[index]=after
                content=bytes(data)
            elif 'tts_threads' in row:
                cfg=json.loads(content)
                for model in cfg['models']:
                    for opt in model['options']:
                        if opt['n'] in ('acoustic_thread','vocoder_thread'):
                            opt['v']=str(row['tts_threads'])
                content=(json.dumps(cfg,indent=2,ensure_ascii=False)+'\n').encode()
            require(len(content)==row['bytes'] and digest(content)==row['sha256'],'原厂组件转换校验失败 '+path)
            return content
    raise ValueError('备份缺少匹配的原厂组件 '+row['sources'][0][1])

def write_new(path,content,mode):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('xb') as f:f.write(content)
    path.chmod(mode)
    require(filehash(path)==digest(content),'生成文件回读校验失败')

def gunzip_sparse(source,dest):
    size=0
    with gzip.open(source,'rb') as src,dest.open('xb') as dst:
        while b:=src.read(256*1024):
            if b.count(0)==len(b):dst.seek(len(b),1)
            else:dst.write(b)
            size+=len(b)
        dst.truncate(size)
    return size

def verify_platform(platform):
    spec=json.loads((HERE/'platform-files.json').read_bytes())
    for name,h in spec.items():
        p=platform/name
        require(p.resolve().is_relative_to(platform) and p.is_file() and not p.is_symlink() and filehash(p)==h,'平台组件缺失或校验失败 '+name)
    return spec

def stage(args,tmp,result):
    # The private staging directory encloses the images. Android must be able to traverse their runtime directories.
    os.umask(0o022)
    source=DiskFile(args.backup)
    ident=identity(source)
    require(region_hash(source,PARTS['boot_a'][0]*512,PARTS['boot_a'][1]*512)==STOCK_BOOT,'本入口只接受保留原厂 boot A 的首次安装备份')
    initial=args.backup.stat()
    old_meta=source.read(RANGES[-1][0],512)
    new_meta=trial(old_meta)
    print('已识别设备和原厂启动分区，正在核对平台与本机备份。',flush=True)
    inputs=verify_platform(args.platform)
    recipe=json.loads((HERE/'factory-files.json').read_bytes())
    modes=json.loads((HERE/'board-modes.json').read_bytes())
    apps=json.loads((HERE/'apps.json').read_bytes())
    with mounted(args.backup,tmp/'factory','system_b') as factory,mounted(args.backup,tmp/'source','userdisk') as user:
        version=(factory/'Version').read_text()
        require('Version: 3.4.6' in [s.strip() for s in version.splitlines()],'需要原厂 B 3.4.6')
        require(filehash(factory/'etc/fstab')=='e4ce5d29bad14b50445e8b37a90c46f6050f1828d62d8d5799c0e13b74d1ae74','原厂 B 挂载表与首次安装版本不符')
        require(not os.path.lexists(user/'cherry-android'),'检测到已有双系统，请使用升级流程')
        for folder in ['resource/localdict','resource/localsound','miniapp/data/mini_app/pkg']:
            d=user/folder
            require(d.resolve().is_relative_to(user) and d.is_dir() and any(d.iterdir()),'缺少原厂目录 '+folder)
        roots={'factory':factory,'user':user}
        # Verify every source before allocating the output image or editing its copy.
        for key,row in recipe.items():read_stock(row,roots,key)
        print('已核验原厂资源，正在离线生成用户分区副本。',flush=True)
        image=tmp/'userdisk.ext4'
        start,n=RANGES[0]
        run('dd','if='+str(args.backup),'of='+str(image),'bs=4M','iflag=skip_bytes,count_bytes',
            'skip='+str(start),'count='+str(n),'conv=sparse','status=none')
        run('e2fsck','-fn',image)
        with mounted(image,tmp/'target',ro=False) as volume:
            board=volume/'cherry-android'
            board.mkdir()
            for key in ('system-controls','vendor-desktop'):
                p=board/(key+'.ext4')
                materialize(args.platform,key,p,factory)
                run('e2fsck','-fn',p)
            datafile=board/'data-desktop.ext4'
            require(gunzip_sparse(args.platform/'data-clean.ext4.gz',datafile)==2684354560,'Android 数据模板错误')
            with mounted(datafile,tmp/'seed',ro=False) as seed:
                for key in [*recipe,*inputs]:
                    group,_,rel=key.partition('/')
                    if group not in ('board','data','apps'):continue
                    content=read_stock(recipe[key],roots,key) if key in recipe else (args.platform/key).read_bytes()
                    if group=='apps':
                        matches=[(pkg,e if len(es)>1 else 'base.apk') for pkg,es in apps.items() for e in [x['name'] for x in es] if e==rel]
                        require(len(matches)==1,'APK 映射无效 '+key)
                        pkg,apk=matches[0]
                        rel='first-boot/'+pkg+'/'+apk
                        group='board'
                    if rel in ('boot-control/cherry-bootctl','desktop/manual-exit.sh'):
                        content=personalize(content,ident['serial'])
                    mode=modes[rel] if group=='board' else (0o755 if rel.endswith(('/stock-worker','/ld-linux-aarch64.so.1')) else 0o644)
                    write_new((board if group=='board' else seed)/rel,content,mode)
            run('e2fsck','-fn',datafile)
            helper=tmp/'stock-bootctl'
            write_new(helper,personalize((args.platform/'factory/stock-bootctl').read_bytes(),ident['serial']),0o755)
            register_button(volume,ident['serial'],True,stock=args.platform/'factory',helper_file=helper)
        run('e2fsck','-fn',image)
    bootfiles=args.platform/'boot'
    boot=assemble((bootfiles/'header').read_bytes(),personalize((bootfiles/'kernel').read_bytes(),ident['serial']),
                  (bootfiles/'ramdisk').read_bytes(),(bootfiles/'resource').read_bytes())
    boot=boot.ljust(RANGES[1][1],b'\0')
    blocks=result/'blocks'
    blocks.mkdir(mode=0o700)
    rows=[]
    with image.open('rb') as userdisk:
        for index,(start,n) in enumerate(RANGES):
            for pos,count in pieces(start,n):
                old=source.read(pos,count)
                if index==0:new=userdisk.read(count)
                elif index==1:new=boot[pos-start:pos-start+count]
                else:new=new_meta
                require(len(new)==count,'生成镜像读取不完整')
                if old!=new:
                    h=digest(new)
                    if not (blocks/h).exists():write_new(blocks/h,new,0o600)
                    rows.append([pos,count,digest(old),h])
    manifest=dict(format=1,device=ident,backup_sha256=region_hash(source,0,SIZE),changes=rows)
    (result/'update.json').write_text(json.dumps(manifest,indent=1)+'\n')
    require((initial.st_size,initial.st_mtime_ns,initial.st_ino)==
            (args.backup.stat().st_size,args.backup.stat().st_mtime_ns,args.backup.stat().st_ino),'源备份在准备期间发生变化')
    (result/'update.sha256').write_text(filehash(result/'update.json')+'\n')
    print('本机安装文件已生成，差异块数',len(rows),flush=True)
