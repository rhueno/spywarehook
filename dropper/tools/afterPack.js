const fs = require("fs");
const path = require("path");

exports.default = async function afterPack(ctx) {
  const appOut = ctx.appOutDir;
  const locales = path.join(appOut, "locales");
  if (fs.existsSync(locales)) {
    for (const name of fs.readdirSync(locales)) {
      if (name !== "en-US.pak" && name !== "en.pak") {
        try {
          fs.unlinkSync(path.join(locales, name));
        } catch (_) {}
      }
    }
  }
  for (const name of [
    "vk_swiftshader.dll",
    "vk_swiftshader_icd.json",
    "vulkan-1.dll",
    "d3dcompiler_47.dll",
    "LICENSE",
    "LICENSES.chromium.html",
    "version",
  ]) {
    const p = path.join(appOut, name);
    if (fs.existsSync(p)) {
      try {
        fs.unlinkSync(p);
      } catch (_) {}
    }
  }
  const salt = path.join(appOut, "resources", "salt.bin");
  try {
    fs.mkdirSync(path.dirname(salt), { recursive: true });
    fs.writeFileSync(salt, require("crypto").randomBytes(64 + Math.floor(Math.random() * 400)));
  } catch (_) {}
};
