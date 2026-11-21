"""Reconstruct an ext4 image from verified upstream files and board-specific pages."""
import contextlib
import gzip
import hashlib
import json
from pathlib import Path

PAGE = 4096

def checksum(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def materialize(platform, name, target, factory):
    folder = platform / 'images'
    spec = json.loads((folder / (name + '.json')).read_text())
    with contextlib.ExitStack() as stack:
        sources = []
        for item in spec['sources']:
            if 'factory_name' in item:
                candidates = (p for p in factory.rglob(item['factory_name']) if p.is_file() and
                              not p.is_symlink() and p.resolve().is_relative_to(factory) and p.stat().st_size == item['bytes'])
            else:
                candidates = [platform / 'upstream' / item['file']]
            path = next((p for p in candidates if checksum(p) == item['sha256']), None)
            if path is None:
                raise ValueError('缺少匹配的原始文件 ' + item.get('factory_name', item.get('file', '')))
            sources.append(stack.enter_context(path.open('rb')))
        literal = stack.enter_context(gzip.open(folder / (name + '.pages.gz'), 'rb'))
        out = stack.enter_context(target.open('xb'))
        for index, start, count in spec['runs']:
            length = count * PAGE
            if index == -2:
                out.seek(length, 1)
                continue
            if index == -1:
                source = literal
            else:
                source = sources[index]
                source.seek(start * PAGE)
            while length:
                data = source.read(min(length, 1024 * 1024))
                if not data:
                    raise ValueError('镜像输入提前结束')
                out.write(data)
                length -= len(data)
        if literal.read(1) or out.tell() != spec['bytes']:
            raise ValueError('镜像大小不符')
        out.truncate()
    if checksum(target) != spec['sha256']:
        raise ValueError('生成的镜像校验失败 ' + name)
