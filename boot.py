"""Personalize the fixed-length build marker and assemble an Android v0 boot image."""
import hashlib
import struct
import subprocess
from disk import require

MARK=b'CHERRYDEVICE0000'

def assemble(header,kernel,ramdisk,resource):
    header=bytearray(header)
    page=struct.unpack_from('<I',header,36)[0]
    require(header[:8]==b'ANDROID!' and len(header)==page and page in (2048,4096),'未知启动镜像头')
    compressed=subprocess.run(['lz4','-c','-1','-'],input=kernel,capture_output=True,check=True).stdout
    require(subprocess.run(['lz4','-dc'],input=compressed,capture_output=True,check=True).stdout==kernel,'内核压缩校验失败')
    k=compressed+struct.pack('<I',len(kernel))
    acc=hashlib.sha1()
    body=bytearray()
    for offset,data in [(8,k),(16,ramdisk),(24,resource)]:
        struct.pack_into('<I',header,offset,len(data))
        acc.update(data)
        acc.update(struct.pack('<I',len(data)))
        body.extend(data)
        body.extend(bytes(-len(data)%page))
    header[576:608]=acc.digest()+bytes(12)
    require(len(header)+len(body)<=16*1024**2,'启动镜像超过分区大小')
    return bytes(header)+body

def personalize(contents,serial):
    require(len(serial)==len(MARK) and contents.count(MARK)==1,'设备标识模板错误')
    return contents.replace(MARK,serial.encode('ascii'))
