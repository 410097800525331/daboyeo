const stateBadge = document.getElementById("stateBadge");
const repoPath = document.getElementById("repoPath");
const serverValue = document.getElementById("serverValue");
const tokenValue = document.getElementById("tokenValue");
const providersValue = document.getElementById("providersValue");
const pidValue = document.getElementById("pidValue");
const healthOutput = document.getElementById("healthOutput");
const logOutput = document.getElementById("logOutput");
const startButton = document.getElementById("startButton");
const killButton = document.getElementById("killButton");
const healthButton = document.getElementById("healthButton");
const clearLogButton = document.getElementById("clearLogButton");

function renderStatus(status) {
  repoPath.textContent = status.repoRoot || "";
  serverValue.textContent = status.server || "-";
  tokenValue.textContent = status.tokenPresent ? "present" : "missing";
  providersValue.textContent = status.providers || "-";
  pidValue.textContent = status.pid ? String(status.pid) : "-";
  logOutput.textContent = (status.logs || []).join("\n");

  stateBadge.textContent = status.running ? "running" : status.lastError ? "error" : "stopped";
  stateBadge.classList.toggle("running", Boolean(status.running));
  stateBadge.classList.toggle("error", Boolean(!status.running && status.lastError));

  startButton.disabled = status.running || !status.tokenPresent;
  killButton.disabled = !status.running;
}

async function refreshStatus() {
  renderStatus(await window.daboyeoBridge.getStatus());
}

startButton.addEventListener("click", async () => {
  renderStatus(await window.daboyeoBridge.start());
});

killButton.addEventListener("click", async () => {
  renderStatus(await window.daboyeoBridge.kill());
});

healthButton.addEventListener("click", async () => {
  const health = await window.daboyeoBridge.health();
  healthOutput.textContent = JSON.stringify(health, null, 2);
  await refreshStatus();
});

clearLogButton.addEventListener("click", async () => {
  renderStatus(await window.daboyeoBridge.clearLogs());
});

window.daboyeoBridge.onStatus(renderStatus);
refreshStatus();
setInterval(refreshStatus, 3000);
