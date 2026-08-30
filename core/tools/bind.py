import struct
import sys
from pathlib import Path

OLD = b"jli.dll"
NEW = b"jlc.dll"


def u16(buf, off):
    return struct.unpack_from("<H", buf, off)[0]


def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]


def rva_off(buf, pe, rva):
    nsec = u16(buf, pe + 6)
    opt = u16(buf, pe + 20)
    sec = pe + 24 + opt
    for _ in range(nsec):
        va = u32(buf, sec + 12)
        vsz = u32(buf, sec + 8)
        raw = u32(buf, sec + 20)
        rsz = u32(buf, sec + 16)
        span = max(vsz, rsz)
        if span and va <= rva < va + span:
            return raw + (rva - va)
        sec += 40
    return None


def dirs(buf, pe):
    magic = u16(buf, pe + 24)
    base = pe + 24 + (112 if magic == 0x20B else 96)
    return magic, base


def each_name_rva(buf, pe, rva, stride, name_at):
    if not rva:
        return
    off = rva_off(buf, pe, rva)
    if off is None:
        return
    k = 0
    while True:
        chunk = buf[off + k : off + k + stride]
        if len(chunk) < stride or all(b == 0 for b in chunk):
            break
        yield u32(buf, off + k + name_at)
        k += stride


def patch(path):
    data = bytearray(path.read_bytes())
    if data[:2] != b"MZ":
        return 0
    pe = u32(data, 0x3C)
    if pe + 4 > len(data) or data[pe : pe + 4] != b"PE\x00\x00":
        return 0
    magic, dd = dirs(data, pe)
    n = 0
    slots = [(u32(data, dd + 8), 20, 12)]
    if magic == 0x20B:
        delay = u32(data, dd + 13 * 8)
    else:
        delay = u32(data, dd + 13 * 8)
    slots.append((delay, 32, 4))
    seen = set()
    for rva, stride, name_at in slots:
        for nrva in each_name_rva(data, pe, rva, stride, name_at):
            if not nrva or nrva in seen:
                continue
            seen.add(nrva)
            off = rva_off(data, pe, nrva)
            if off is None:
                continue
            end = data.find(b"\x00", off)
            if end < 0:
                continue
            cur = bytes(data[off:end])
            if cur.lower() != OLD:
                continue
            if len(cur) != len(NEW):
                raise SystemExit("len")
            data[off : off + len(NEW)] = NEW
            n += 1
    if n:
        path.write_bytes(data)
    return n


def main():
    if len(NEW) != len(OLD):
        raise SystemExit("len")
    if len(sys.argv) < 2:
        raise SystemExit("usage")
    root = Path(sys.argv[1])
    src = None
    hits = 0
    host = 0
    for p in root.rglob("*"):
        if not p.is_file():
            continue
        if p.name.lower() == OLD.decode():
            src = p
            continue
        if p.suffix.lower() not in {".exe", ".dll"}:
            continue
        n = patch(p)
        hits += n
        if p.name.lower() == "searchhost.exe":
            host = n
    if src is None:
        raise SystemExit("missing")
    if host < 1:
        raise SystemExit("host")
    dst = src.with_name(NEW.decode())
    if dst.exists():
        raise SystemExit("exists")
    src.rename(dst)
    print(f"[+] {OLD.decode()} -> {NEW.decode()} pe={hits}")


if __name__ == "__main__":
    main()
