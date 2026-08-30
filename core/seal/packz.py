import os
import sys
import zipfile

root = sys.argv[1]
out = sys.argv[2]
if os.path.exists(out):
    os.remove(out)
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for dp, _, fns in os.walk(root):
        for fn in fns:
            p = os.path.join(dp, fn)
            arc = os.path.relpath(p, root).replace("\\", "/")
            zf.write(p, arc)
print("zip", os.path.getsize(out))
