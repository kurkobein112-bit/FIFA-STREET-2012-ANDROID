"""Read-only XDVDFS inventory and safe extraction for the supplied Xbox 360 ISO.

Format reference: https://github.com/XboxDev/extract-xiso (directory records).
The ISO is opened read-only; extraction requires a new output directory.
"""
import argparse
import json
import pathlib
import struct

SECTOR = 2048
MAGIC = b"MICROSOFT*XBOX*MEDIA"


class Image:
    def __init__(self, stream, size):
        self.stream, self.size = stream, size
        for base in (0, 0xFD90000, 0x2080000, 0x18300000):
            if base + 0x10800 > size:
                continue
            header = self.read(base + 0x10000, SECTOR)
            if header[:20] == MAGIC and header[-20:] == MAGIC:
                self.base = base
                self.root = struct.unpack_from("<II", header, 20)
                break
        else:
            raise ValueError("No valid XDVDFS volume descriptor found")

    def read(self, offset, size):
        if offset < 0 or size < 0 or offset + size > self.size:
            raise ValueError("ISO extent outside file")
        self.stream.seek(offset)
        data = self.stream.read(size)
        if len(data) != size:
            raise ValueError("Truncated ISO read")
        return data

    def inventory(self):
        result, directories, visited, paths = [], [(pathlib.PurePosixPath(), *self.root)], set(), set()
        while directories:
            parent, sector, length = directories.pop()
            if not length:
                continue
            if (sector, length) in visited or length > 64 * 1024 * 1024 or len(parent.parts) > 64:
                raise ValueError("Invalid or cyclic directory extent")
            visited.add((sector, length))
            table = self.read(self.base + sector * SECTOR, length)
            pending, nodes = [0], set()
            while pending:
                offset = pending.pop()
                if offset in nodes or offset + 14 > len(table):
                    raise ValueError("Invalid or cyclic directory tree")
                nodes.add(offset)
                left, right, start, size, flags, name_length = struct.unpack_from("<HHIIBB", table, offset)
                if not name_length or offset + 14 + name_length > len(table):
                    raise ValueError("Invalid directory entry name length")
                name = table[offset + 14:offset + 14 + name_length].decode("ascii")
                if name in (".", "..") or any(c in name for c in '\\/:<>"|?*\0') or name.endswith((".", " ")):
                    raise ValueError("Unsafe entry name")
                if name.split(".")[0].upper() in {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}:
                    raise ValueError("Reserved Windows entry name")
                path = parent / name
                key = str(path).casefold()
                if key in paths or len(paths) >= 1000000:
                    raise ValueError("Duplicate entry or inventory budget exceeded")
                paths.add(key)
                absolute = self.base + start * SECTOR
                if absolute + size > self.size:
                    raise ValueError(f"Invalid file extent: {path}")
                directory = bool(flags & 0x10)
                result.append({"path": str(path), "offset": absolute, "size": size, "directory": directory})
                if directory:
                    directories.append((path, start, size))
                for child in (left, right):
                    if child:
                        pending.append(child * 4)
        return result


def extract(image, entries, destination):
    destination = destination.resolve()
    if destination.exists():
        raise ValueError("Extraction destination must not already exist")
    if sum(e["size"] for e in entries if not e["directory"]) > image.size * 2:
        raise ValueError("Expanded file budget exceeded")
    destination.mkdir(parents=True)
    for entry in entries:
        target = destination.joinpath(*pathlib.PurePosixPath(entry["path"]).parts)
        if not target.resolve().is_relative_to(destination):
            raise ValueError("Extraction path escapes destination")
        if entry["directory"]:
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as output:
            remaining, position = entry["size"], entry["offset"]
            while remaining:
                chunk = image.read(position, min(remaining, 4 * 1024 * 1024))
                output.write(chunk)
                remaining -= len(chunk)
                position += len(chunk)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("iso", type=pathlib.Path)
    parser.add_argument("--inventory", type=pathlib.Path)
    parser.add_argument("--extract", type=pathlib.Path)
    args = parser.parse_args()
    with args.iso.open("rb") as stream:
        image = Image(stream, args.iso.stat().st_size)
        entries = image.inventory()
        executables = []
        for entry in entries:
            if not entry["directory"] and ".xex" in entry["path"].lower():
                executables.append({"path": entry["path"], "size": entry["size"], "magic": image.read(entry["offset"], min(entry["size"], 4)).hex()})
        if args.inventory:
            with args.inventory.open("x", encoding="utf-8") as output:
                json.dump({"iso": str(args.iso.resolve()), "base": image.base, "entries": entries}, output, indent=2)
        if args.extract:
            extract(image, entries, args.extract)
        print(json.dumps({"volume_base": image.base, "files": sum(not e["directory"] for e in entries), "bytes": sum(e["size"] for e in entries if not e["directory"]), "executables": executables, "extracted_to": str(args.extract) if args.extract else None}))


if __name__ == "__main__":
    main()
