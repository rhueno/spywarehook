const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const root = path.join(__dirname, "..");
const res = path.join(root, "res");
const jarPath = path.join(res, "core.jar");
const zipPath = path.join(res, "app.zip");
const outPath = path.join(res, "payload.bin");
const keyPath = path.join(root, "src", "key.raw");

function die(m) {
  console.error("[!]", m);
  process.exit(1);
}

if (!fs.existsSync(jarPath)) die("res/core.jar yok");
if (!fs.existsSync(zipPath)) die("res/app.zip yok");

const jar = fs.readFileSync(jarPath);
const zdata = fs.readFileSync(zipPath);
const hdr = 28;
const jarOff = hdr;
const zipOff = hdr + jar.length;
const padN = 128 + crypto.randomBytes(1)[0] * 4;
const extra = crypto.randomBytes(padN);

const plain = Buffer.alloc(hdr + jar.length + zdata.length + extra.length);
plain.write("NF2\0", 0, 4, "binary");
plain.writeUInt32LE(jarOff, 4);
plain.writeUInt32LE(jar.length, 8);
plain.writeUInt32LE(zipOff, 12);
plain.writeUInt32LE(zdata.length, 16);
plain.writeUInt32LE(extra.length, 20);
jar.copy(plain, jarOff);
zdata.copy(plain, zipOff);
extra.copy(plain, zipOff + zdata.length);

const key = crypto.randomBytes(32);
const nonce = crypto.randomBytes(12);
const cipher = crypto.createCipheriv("aes-256-gcm", key, nonce);
const enc = Buffer.concat([cipher.update(plain), cipher.final()]);
const tag = cipher.getAuthTag();
const sealed = Buffer.concat([nonce, enc, tag]);
fs.writeFileSync(outPath, sealed);
fs.writeFileSync(keyPath, key);

const hex = [...key].map((b) => b.toString(16).padStart(2, "0")).join("");
console.log("[+] payload.bin size=" + sealed.length);
console.log("[+] key=" + hex.slice(0, 16) + "...");
