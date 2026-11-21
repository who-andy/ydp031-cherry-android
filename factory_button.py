"""Register the factory desktop button in an offline, mounted userdisk copy (Linux)."""
import argparse
import fcntl
import hashlib
import json
import os
import re
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
STOCK = HERE.parent / 'stock-return'
APP_ID = '9000000000000310'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_dir(root, relative, create=False):
    directory = root
    for part in relative.parts:
        directory = directory / part
        if create:
            directory.mkdir(exist_ok=True, mode=0o755)
        if not stat.S_ISDIR(directory.lstat().st_mode):
            raise ValueError(f'Expected a real directory at {directory}')
    return directory


def sync_dir(directory):
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def existing(path):
    try:
        mode = path.lstat().st_mode
    except FileNotFoundError:
        return None
    if not stat.S_ISREG(mode):
        raise ValueError(f'Expected a regular file at {path}')
    return path.read_bytes()


def commit(path, content, mode, owner=None):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as f:
        temp = Path(f.name)
        try:
            f.write(content)
            f.flush()
            if owner:
                os.fchown(f.fileno(), *owner)
            os.fchmod(f.fileno(), mode)
            os.fsync(f.fileno())
            os.replace(temp, path)
            sync_dir(path.parent)
        finally:
            temp.unlink(missing_ok=True)


def install(root, serial, apply, *, stock=STOCK, helper_file=None):
    root = root.resolve(strict=True)
    registry_dir = safe_dir(root, Path('miniapp/data/mini_app/pkg'))
    lock = os.open(registry_dir, os.O_RDONLY | os.O_DIRECTORY)
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        registry = registry_dir / 'packages.json'
        original = existing(registry)
        info = registry.stat()
        data = json.loads(original)
        entry = json.loads((stock/'package-entry.json').read_bytes())
        assert entry['appid'] == APP_ID
        matches = [row for row in data['packages'] if row.get('appid') == APP_ID]
        if matches not in ([], [entry]):
            raise ValueError('A different or duplicate entry already uses the button ID')
        files = {}
        target = registry_dir.relative_to(root) / APP_ID / 'a'
        for name in ('app.js.bin', 'index.js.bin', 'app_icon.png', 'manifest.json',
                     'libs/libjsapi_cherry_return.so'):
            files[target/name] = ((stock/'installed-app'/name).read_bytes(), 0o644)
        helper = (helper_file or HERE/'out'/'devices'/serial/'stock-bootctl').read_bytes()
        if not helper.startswith(b'\x7fELF') or b'androidboot.serialno='+serial.encode()+b' ' not in helper:
            raise ValueError('Factory switch helper is for a different pen')
        service = Path('cherry-android/stock-return')
        files[service/'stock-bootctl'] = (helper, 0o755)
        files[service/'reboot-android.sh'] = ((stock/'reboot-android.sh').read_bytes(), 0o755)
        # Check every collision before creating the first destination.
        changes = []
        for rel, (content, mode) in files.items():
            for ancestor in reversed(rel.parents):
                if (root/ancestor).is_symlink():
                    raise ValueError(f'Unexpected symlink: {ancestor}')
            old = existing(root/rel)
            if old is not None and old != content:
                raise ValueError(f'Different file already present: {rel}')
            if old is None or stat.S_IMODE((root/rel).stat().st_mode) != mode or (root/rel).stat().st_uid != 0:
                changes.append(rel)
        button = root/target.parent
        if button.exists():
            allowed = {root/path for path in files}
            allowed.update(parent for path in list(allowed) for parent in path.parents)
            # Factory miniapp creates a sibling data/ after the button is opened.
            # Preserve it in place when the registry identifies this exact app.
            user_data = button/'data'
            if matches and user_data.exists() and stat.S_ISDIR(user_data.lstat().st_mode):
                allowed.add(user_data)
            if (any(path not in allowed for path in button.iterdir()) or
                    any(path not in allowed for path in (root/target).rglob('*'))):
                raise ValueError('Unrecognized content in the button directory')
        print(f'{len(changes)} file(s) to stage, registration {"present" if matches else "needed"}')
        if not apply:
            return
        if os.geteuid() != 0:
            raise ValueError('Apply to the offline copy as root to preserve file ownership')
        if not matches:
            backup = root/service/('packages-before-'+digest(original)+'.json')
            safe_dir(root, service, True)
            prior = existing(backup)
            if prior is None:
                commit(backup, original, 0o600)
            elif prior != original:
                raise ValueError('Registration backup differs')
        for rel in changes:
            safe_dir(root, rel.parent, True)
            commit(root/rel, *files[rel])
        if not matches:
            if existing(registry) != original:
                raise ValueError('Registration changed during staging')
            data['packages'].append(entry)
            commit(registry, json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode(),
                   stat.S_IMODE(info.st_mode), (info.st_uid, info.st_gid))
        assert json.loads(registry.read_bytes()) == data
        for rel, (content, _) in files.items():
            if existing(root/rel) != content:
                raise ValueError(f'Readback mismatch: {rel}')
        print('Factory button files and registry verified')
    finally:
        os.close(lock)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--userdisk', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9]{8,32}', args.serial):
        parser.error('invalid serial')
    install(args.userdisk, args.serial, args.apply)
