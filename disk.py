"""Identification and bounded I/O for the 16 GB Cherry board."""
import binascii
import hashlib
import os
import re
import struct
import subprocess
from pathlib import Path

SECTOR = 512
SIZE = 15518924800
BLOCK = 4 * 1024**2
PARTS = dict(zip(
    ('uboot_a','uboot_b','trust_a','trust_b','misc','boot_a','boot_b','recovery','system_a','system_b','userdata','userdisk'),
    ((16384,8192),(24576,8192),(32768,8192),(40960,8192),(49152,8192),(57344,32768),
     (90112,32768),(122880,131072),(253952,5242880),(5496832,5242880),(10739712,1048576),(11788288,18522048))))
STOCK_BOOT = 'a7cae51267038146535f8921b53fc1f469f0fe5d33e56bebbd5fce468b3a6f76'
META = (PARTS['misc'][0] + 4) * SECTOR
RANGES = [(PARTS[k][0]*SECTOR, PARTS[k][1]*SECTOR) for k in ('userdisk','boot_a')] + [(META,SECTOR)]

def require(ok, message):
    if not ok:
        raise ValueError(message)

def digest(data):
    return hashlib.sha256(data).hexdigest()

def pieces(start, length):
    for off in range(start,start+length,BLOCK):
        yield off,min(BLOCK,start+length-off)

def region_hash(reader, start, length):
    acc = hashlib.sha256()
    for pos,n in pieces(start,length):
        acc.update(reader.read(pos,n))
    return acc.hexdigest()

class DiskFile:
    def __init__(self,path,mode='rb'):
        require(path.is_file() and not path.is_symlink() and path.stat().st_size==SIZE,
                '需要本机的完整 16 GB eMMC 用户区普通镜像文件')
        self.file=path.open(mode)
    def read(self,pos,n):
        self.file.seek(pos)
        data=self.file.read(n)
        require(len(data)==n,'磁盘读取不完整')
        return data
    def write(self,pos,data):
        self.file.seek(pos)
        require(self.file.write(data)==len(data),'写入不完整')
        self.file.flush()
        os.fsync(self.file.fileno())

class Loader:
    def __init__(self,tool,tmp):
        self.tool,self.tmp=tool,tmp
        inventory=self.command('ld')
        items=[x for x in inventory.splitlines() if 'Vid=' in x]
        require(len(items)==1 and re.search(r'Vid=0x2207,Pid=0x330d,LocationID=[0-9a-f]+\s+Loader\s*$',items[0],re.I),
                '需要一台已由设备所有者进入 Rockchip Loader 维护模式的目标词典笔')
    def command(self,*args):
        p=subprocess.run([self.tool,*map(str,args)],capture_output=True,text=True,timeout=240)
        require(not p.returncode and 'fail' not in (p.stdout+p.stderr).lower(),'Loader 操作失败 '+(p.stdout+p.stderr)[-300:])
        return p.stdout
    def read(self,pos,n):
        first,last=pos//SECTOR,(pos+n+SECTOR-1)//SECTOR
        path=self.tmp/'read.bin'
        path.unlink(missing_ok=True)
        self.command('rl',first,last-first,path)
        raw=path.read_bytes()
        require(len(raw)==(last-first)*SECTOR,'Loader 回读长度不符')
        return raw[pos-first*SECTOR:pos-first*SECTOR+n]
    def write(self,pos,data):
        require(pos%SECTOR==0 and len(data)%SECTOR==0,'写入未对齐')
        require(data[:4] not in (bytes.fromhex('3aff26ed'),b'UBI#'),'工具会把本区块误认为稀疏镜像')
        path=self.tmp/'write.bin'
        path.write_bytes(data)
        self.command('wl',pos//SECTOR,path)

def identity(reader):
    def gpt(lba,alternate):
        h=bytearray(reader.read(lba*SECTOR,SECTOR))
        require(h[:8]==b'EFI PART' and struct.unpack_from('<QQ',h,24)==(lba,alternate),'GPT 头不匹配')
        sz,crc=struct.unpack_from('<II',h,12)
        struct.pack_into('<I',h,16,0)
        require(92<=sz<=512 and binascii.crc32(h[:sz])==crc,'GPT 头校验失败')
        where,count,width,crc=struct.unpack_from('<QIII',h,72)
        require(width==128 and 12<=count<=128,'不支持的 GPT 表长度')
        require(where==(2 if lba==1 else lba-(count*width+511)//512),'GPT 表偏移不符')
        table=reader.read(where*512,count*width)
        require(binascii.crc32(table)==crc,'GPT 分区校验失败')
        return bytes(h[56:72]),table
    last=SIZE//512-1
    guid,table=gpt(1,last)
    require((guid,table)==gpt(last,1),'主副 GPT 不一致')
    parts=[]
    for i in range(0,len(table),128):
        row=table[i:i+128]
        if any(row[:16]):
            a,b=struct.unpack_from('<QQ',row,32)
            parts.append((row[56:128].decode('utf-16le').rstrip('\0'),(a,b-a+1)))
    require(parts==list(PARTS.items()),'不是支持的 YDP031CHNSTD 分区布局')
    serials=[reader.read(3671040+65536*i,32).split(b'\0')[0] for i in range(4)]
    require(len(set(serials))==1 and re.fullmatch(rb'[A-Za-z0-9]{16}',serials[0]),'无法从四份设备标识确认同一序列号')
    start,n=PARTS['boot_b']
    require(region_hash(reader,start*512,n*512)==STOCK_BOOT,'原厂 B 启动镜像不匹配')
    return dict(serial=serials[0].decode(),gpt=digest(guid+table))

def trial(old):
    require(old[:5]==b'\0AB0\x01' and old[28:32]==struct.pack('>I',binascii.crc32(old[:28])),'A/B 启动记录无效')
    for i in (8,12):
        require(old[i]<=15 and old[i+1]<=7 and old[i+2]<=1,'无效的槽位状态')
    require(old[12]>0 and (old[13]>0 or old[14]==1),'原厂 B 不可启动')
    new=bytearray(old)
    new[8:11]=bytes((15,2,0))
    new[12]=14
    new[28:32]=struct.pack('>I',binascii.crc32(new[:28]))
    return bytes(new)
