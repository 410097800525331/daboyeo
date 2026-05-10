const { app, BrowserWindow, ipcMain } = require("electron");
const fs = require("node:fs");
const http = require("node:http");
const https = require("node:https");
const path = require("node:path");
const { spawn, spawnSync } = require("node:child_process");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const bridgeScript = path.join(repoRoot, "scripts", "ai_bridge_agent.py");
const envFile = path.join(repoRoot, ".env");
const maxLogLines = 400;

let mainWindow = null;
let bridgeProcess = null;
let startedAt = null;
let lastError = "";
let logs = [];

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 920,
    height: 700,
    minWidth: 760,
    minHeight: 620,
    title: "DABOYEO Bridge",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  mainWindow.loadFile(path.join(__dirname, "index.html"));
}

function loadDotEnv() {
  const loaded = {};
  if (!fs.existsSync(envFile)) {
    return loaded;
  }
  const lines = fs.readFileSync(envFile, "utf8").split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const index = line.indexOf("=");
    const key = line.slice(0, index).trim();
    let value = line.slice(index + 1).trim();
    if (!key) {
      continue;
    }
    if (value.length >= 2 && value[0] === value[value.length - 1] && ["\"", "'"].includes(value[0])) {
      value = value.slice(1, -1);
    }
    loaded[key] = value;
  }
  return loaded;
}

function mergedEnv() {
  return { ...process.env, ...loadDotEnv() };
}

function runtimeConfig() {
  const env = mergedEnv();
  return {
    env,
    server: env.DABOYEO_BRIDGE_SERVER || "http://127.0.0.1:5500",
    providers: env.DABOYEO_BRIDGE_PROVIDERS || "codex",
    tokenPresent: Boolean((env.DABOYEO_AI_BRIDGE_TOKEN || "").trim()),
  };
}

function appendLog(source, chunk) {
  const config = runtimeConfig();
  const token = config.env.DABOYEO_AI_BRIDGE_TOKEN || "";
  const text = String(chunk || "")
    .split(/\r?\n/)
    .map((line) => sanitizeLogLine(line, token))
    .filter(Boolean);
  for (const line of text) {
    logs.push(`[${new Date().toLocaleTimeString()}] ${source}: ${line}`);
  }
  if (logs.length > maxLogLines) {
    logs = logs.slice(logs.length - maxLogLines);
  }
  broadcastStatus();
}

function sanitizeLogLine(line, token) {
  let sanitized = line;
  if (token) {
    sanitized = sanitized.split(token).join("[secret]");
  }
  sanitized = sanitized.replace(/(DABOYEO_AI_BRIDGE_TOKEN|X-DABOYEO-BRIDGE-TOKEN)(\s*[:=]\s*)\S+/gi, "$1$2[secret]");
  sanitized = sanitized.replace(/(password|secret|token)(\s*[:=]\s*)\S+/gi, "$1$2[secret]");
  return sanitized.trim();
}

function resolvePython(env) {
  const bundledPython = path.join(
    process.env.USERPROFILE || "",
    ".cache",
    "codex-runtimes",
    "codex-primary-runtime",
    "dependencies",
    "python",
    "python.exe"
  );
  const configured = [env.DABOYEO_BRIDGE_PYTHON, env.PYTHON, bundledPython].filter(Boolean);
  const candidates = [...configured, "python", "py", "python3"];
  for (const command of candidates) {
    if (path.isAbsolute(command) && !fs.existsSync(command)) {
      continue;
    }
    const args = command.toLowerCase() === "py" ? ["-3", "--version"] : ["--version"];
    const probe = spawnSync(command, args, { windowsHide: true, encoding: "utf8" });
    if (probe.status === 0) {
      return command;
    }
  }
  return "";
}

function resolveCodexCommand(env) {
  if (env.DABOYEO_CODEX_COMMAND && env.DABOYEO_CODEX_COMMAND.trim()) {
    return env.DABOYEO_CODEX_COMMAND.trim();
  }
  const installedCodex = path.join(
    process.env.LOCALAPPDATA || "",
    "OpenAI",
    "Codex",
    "bin",
    "codex.exe"
  );
  if (fs.existsSync(installedCodex)) {
    return installedCodex;
  }
  return "";
}

function pythonArgs(command) {
  if (path.basename(command).toLowerCase() === "py.exe" || command.toLowerCase() === "py") {
    return ["-3", bridgeScript];
  }
  return [bridgeScript];
}

function bridgeStatus() {
  const config = runtimeConfig();
  return {
    repoRoot,
    server: config.server,
    providers: config.providers,
    tokenPresent: config.tokenPresent,
    running: Boolean(bridgeProcess && !bridgeProcess.killed),
    pid: bridgeProcess ? bridgeProcess.pid : null,
    startedAt,
    lastError,
    logs,
  };
}

function broadcastStatus() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send("bridge:status", bridgeStatus());
  }
}

async function startBridge() {
  if (bridgeProcess && !bridgeProcess.killed) {
    return bridgeStatus();
  }
  const config = runtimeConfig();
  lastError = "";
  if (!config.tokenPresent) {
    lastError = "DABOYEO_AI_BRIDGE_TOKEN is missing.";
    appendLog("gui", lastError);
    return bridgeStatus();
  }
  if (!fs.existsSync(bridgeScript)) {
    lastError = `Bridge script not found: ${bridgeScript}`;
    appendLog("gui", lastError);
    return bridgeStatus();
  }
  const python = resolvePython(config.env);
  if (!python) {
    lastError = "Python runtime was not found. Set DABOYEO_BRIDGE_PYTHON in .env.";
    appendLog("gui", lastError);
    return bridgeStatus();
  }
  const codexCommand = resolveCodexCommand(config.env);
  if (codexCommand) {
    config.env.DABOYEO_CODEX_COMMAND = codexCommand;
  }

  bridgeProcess = spawn(python, pythonArgs(python), {
    cwd: repoRoot,
    env: config.env,
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true,
  });
  startedAt = new Date().toISOString();
  appendLog("gui", `started bridge pid=${bridgeProcess.pid}`);

  bridgeProcess.stdout.on("data", (chunk) => appendLog("stdout", chunk));
  bridgeProcess.stderr.on("data", (chunk) => appendLog("stderr", chunk));
  bridgeProcess.on("error", (error) => {
    lastError = error.message;
    appendLog("error", error.message);
  });
  bridgeProcess.on("exit", (code, signal) => {
    appendLog("gui", `bridge exited code=${code ?? ""} signal=${signal ?? ""}`);
    bridgeProcess = null;
    startedAt = null;
    broadcastStatus();
  });
  return bridgeStatus();
}

async function killBridge() {
  if (!bridgeProcess || bridgeProcess.killed) {
    bridgeProcess = null;
    startedAt = null;
    return bridgeStatus();
  }
  const pid = bridgeProcess.pid;
  appendLog("gui", `killing bridge pid=${pid}`);
  if (process.platform === "win32") {
    spawnSync("taskkill", ["/PID", String(pid), "/T", "/F"], { windowsHide: true, encoding: "utf8" });
  } else {
    bridgeProcess.kill("SIGTERM");
  }
  bridgeProcess = null;
  startedAt = null;
  return bridgeStatus();
}

function fetchJson(url) {
  return new Promise((resolve) => {
    const client = url.startsWith("https:") ? https : http;
    const request = client.get(url, { timeout: 5000 }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        try {
          resolve({ status: response.statusCode, body: JSON.parse(body) });
        } catch (_error) {
          resolve({ status: response.statusCode, body });
        }
      });
    });
    request.on("timeout", () => {
      request.destroy(new Error("timeout"));
    });
    request.on("error", (error) => {
      resolve({ status: 0, error: error.message });
    });
  });
}

async function providerHealth() {
  const { server } = runtimeConfig();
  const url = `${server.replace(/\/$/, "")}/api/recommendation/providers/health`;
  return fetchJson(url);
}

app.whenReady().then(createWindow);

app.on("before-quit", () => {
  if (bridgeProcess && !bridgeProcess.killed) {
    killBridge();
  }
});

app.on("window-all-closed", () => {
  app.quit();
});

ipcMain.handle("bridge:get-status", () => bridgeStatus());
ipcMain.handle("bridge:start", () => startBridge());
ipcMain.handle("bridge:kill", () => killBridge());
ipcMain.handle("bridge:health", () => providerHealth());
ipcMain.handle("bridge:clear-logs", () => {
  logs = [];
  return bridgeStatus();
});
