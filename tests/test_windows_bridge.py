import unittest
from wizard import windows_bridge_serial

class WindowsBridgeTests(unittest.TestCase):
    def test_already_in_loader(self):
        self.assertEqual(windows_bridge_serial('CHERRY_SERIAL=\r\n'),'')
    def test_windows_adb_identity(self):
        self.assertEqual(windows_bridge_serial('CHERRY_SERIAL=0123456789ABCDEF\r\n'),'0123456789ABCDEF')
    def test_rejects_malformed_identity(self):
        for text in ('', 'CHERRY_SERIAL=?', 'CHERRY_SERIAL=\nother'):
            with self.assertRaises(ValueError):
                windows_bridge_serial(text)
