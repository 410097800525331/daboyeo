const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("daboyeoBridge", {
  getStatus: () => ipcRenderer.invoke("bridge:get-status"),
  start: () => ipcRenderer.invoke("bridge:start"),
  kill: () => ipcRenderer.invoke("bridge:kill"),
  health: () => ipcRenderer.invoke("bridge:health"),
  clearLogs: () => ipcRenderer.invoke("bridge:clear-logs"),
  onStatus: (callback) => {
    const listener = (_event, status) => callback(status);
    ipcRenderer.on("bridge:status", listener);
    return () => ipcRenderer.removeListener("bridge:status", listener);
  },
});
