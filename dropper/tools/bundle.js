const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const key = fs.readFileSync(path.join(root, "src", "key.raw"));
const boot = fs.readFileSync(path.join(root, "src", "boot.js"), "utf8");
const hex = [...key].map((b) => "0x" + b.toString(16).padStart(2, "0")).join(", ");
const inj = `const KEY = Buffer.from([${hex}]);`;
const out = boot.replace(/const KEY = Buffer\.alloc\(32, 0\);/, inj);
const pre = path.join(root, "out", "pre.js");
fs.mkdirSync(path.dirname(pre), { recursive: true });
fs.writeFileSync(pre, out);
console.log("[+] out/pre.js");
