"""Generate the public page recipe from the maintainer's clean reference image."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from image_parts import PAGE, checksum

def make(name, target, sources, dest):
    refs, files = {}, []
    for index, (kind, key, path) in enumerate(sources):
        path = Path(path)
        files.append({kind: key, 'bytes': path.stat().st_size, 'sha256': checksum(path)})
        with path.open('rb') as f:
            for page, data in enumerate(iter(lambda: f.read(PAGE), b'')):
                if len(data) == PAGE and any(data):
                    refs[hashlib.sha256(data).digest()] = (index, page)
    runs, zeros = [], bytes(PAGE)
    dest.mkdir(exist_ok=True, parents=True)
    with target.open('rb') as src, gzip.GzipFile(str(dest/(name+'.pages.gz')), 'wb', mtime=0) as literal:
        for data in iter(lambda: src.read(PAGE), b''):
            if len(data) != PAGE:
                raise ValueError('模板没有按页对齐')
            index, page = (-2, 0) if data == zeros else refs.get(hashlib.sha256(data).digest(), (-1, 0))
            if index == -1:
                literal.write(data)
            if runs and runs[-1][0] == index and (index < 0 or page == runs[-1][1] + runs[-1][2]):
                runs[-1][2] += 1
            else:
                runs.append([index, page, 1])
    spec = dict(bytes=target.stat().st_size, sha256=checksum(target), sources=files, runs=runs)
    (dest / (name+'.json')).write_text(json.dumps(spec, separators=(',', ':')) + '\n')

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('name'); p.add_argument('target',type=Path); p.add_argument('dest',type=Path)
    p.add_argument('sources',nargs='+',help='file|factory_name,logical-name,local-path')
    a=p.parse_args()
    make(a.name,a.target,[s.split(',',2) for s in a.sources],a.dest)
