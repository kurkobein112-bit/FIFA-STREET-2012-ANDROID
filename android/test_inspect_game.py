import io
import pathlib
import struct
import tempfile
import unittest
from InspectGame import Image, MAGIC, SECTOR, extract


def sample(name=b"default.xex", left=0):
    data = bytearray(0x18000)
    data[0x10000:0x10014] = MAGIC
    data[0x107ec:0x10800] = MAGIC
    struct.pack_into("<II", data, 0x10014, 40, 256)
    struct.pack_into("<HHIIBB", data, 40 * SECTOR, left, 0, 44, 4, 0, len(name))
    data[40 * SECTOR + 14:40 * SECTOR + 14 + len(name)] = name
    data[44 * SECTOR:44 * SECTOR + 4] = b"XEX2"
    return data


class InspectGameTests(unittest.TestCase):
    def test_inventory_and_extract_exact_bytes_without_changing_input(self):
        data = sample()
        stream = io.BytesIO(data)
        image = Image(stream, len(data))
        entries = image.inventory()
        self.assertEqual(entries[0]["path"], "default.xex")
        with tempfile.TemporaryDirectory() as temporary:
            target = pathlib.Path(temporary) / "new"
            extract(image, entries, target)
            self.assertEqual((target / "default.xex").read_bytes(), b"XEX2")
            with self.assertRaises(ValueError):
                extract(image, entries, target)
        self.assertEqual(stream.getvalue(), data)

    def test_rejects_traversal_and_reserved_names(self):
        for name in (b"../escape", b"CON.txt", b"bad:name", b".."): 
            data = sample(name)
            with self.assertRaises(ValueError):
                Image(io.BytesIO(data), len(data)).inventory()

    def test_rejects_cyclic_directory_tree(self):
        data = sample(left=8)
        struct.pack_into("<HHIIBB", data, 40 * SECTOR + 32, 8, 0, 45, 4, 0, 5)
        data[40 * SECTOR + 46:40 * SECTOR + 51] = b"other"
        with self.assertRaises(ValueError):
            Image(io.BytesIO(data), len(data)).inventory()

    def test_rejects_out_of_bounds_extent_and_bad_descriptor(self):
        data = sample()
        struct.pack_into("<I", data, 40 * SECTOR + 4, 0xffffffff)
        with self.assertRaises(ValueError):
            Image(io.BytesIO(data), len(data)).inventory()
        with self.assertRaises(ValueError):
            Image(io.BytesIO(b"not an ISO"), 10)


if __name__ == "__main__":
    unittest.main()
