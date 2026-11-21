"""Transport and metadata tests. Uses regular files only."""
import binascii
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from boot import MARK,personalize
from disk import Loader,trial

class ReleaseTests(unittest.TestCase):
    def record(self):
        data=bytearray(512)
        data[:5]=b'\0AB0\x01'
        data[8:11]=bytes((14,3,0))
        data[12:15]=bytes((15,0,1))
        data[28:32]=struct.pack('>I',binascii.crc32(data[:28]))
        return bytes(data)

    def test_trial_preserves_factory_success_and_tail(self):
        original=self.record()
        result=trial(original)
        self.assertEqual(result[8:11],bytes((15,2,0)))
        self.assertEqual(result[12:15],bytes((14,0,1)))
        self.assertEqual(result[32:],original[32:])
        self.assertEqual(result[28:32],struct.pack('>I',binascii.crc32(result[:28])))

    def test_corrupt_boot_control_is_rejected(self):
        with self.assertRaises(ValueError):
            trial(self.record()[:28]+bytes(4)+self.record()[32:])

    def test_serial_template_is_unambiguous(self):
        serial='CHERRYTEST000002'
        self.assertEqual(personalize(b'x='+MARK,serial),b'x='+serial.encode())
        for data in (b'',MARK+MARK):
            with self.assertRaises(ValueError):
                personalize(data,serial)

    def test_loader_reads_sector_boundaries(self):
        storage=bytearray(bytes(range(256))*8)
        def command(_unused,op,*args):
            if op=='ld':
                return 'DevNo=1 Vid=0x2207,Pid=0x330d,LocationID=4 Loader'
            lba=args[0]
            if op=='rl':
                count,dest=args[1:]
                dest.write_bytes(storage[lba*512:(lba+count)*512])
            elif op=='wl':
                data=args[1].read_bytes()
                storage[lba*512:lba*512+len(data)]=data
            else:self.fail('unexpected operation')
        with tempfile.TemporaryDirectory() as td,patch.object(Loader,'command',command):
            target=Loader('unused',Path(td))
            self.assertEqual(target.read(495,32),storage[495:527])
            target.write(512,b'x'*512)
            self.assertEqual(target.read(512,512),b'x'*512)
            with self.assertRaises(ValueError):target.write(1,bytes(512))
            with self.assertRaises(ValueError):target.write(512,bytes.fromhex('3aff26ed')+bytes(508))

    def test_loader_refuses_duplicate_devices(self):
        line='DevNo=1 Vid=0x2207,Pid=0x330d,LocationID=4 Loader\n'
        with patch.object(Loader,'command',return_value=line*2),self.assertRaises(ValueError):
            Loader('unused',Path('.'))

if __name__=='__main__':
    unittest.main()
