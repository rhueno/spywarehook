import os
import secrets
import sys


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: salt.py file.exe", file=sys.stderr)
        return 2
    path = sys.argv[1]
    if not os.path.isfile(path):
        print(f"[!] missing {path}", file=sys.stderr)
        return 1
    pad = secrets.token_bytes(secrets.randbelow(3072) + 768)
    with open(path, "ab") as f:
        f.write(pad)
    print(f"[+] salt {len(pad)} -> {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
