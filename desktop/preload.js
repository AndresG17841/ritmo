const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('ritmoDesktop', {
  isDesktop: true,
  popup: (data) => ipcRenderer.send('popup', data),
  onAction: (cb) => ipcRenderer.on('alert-action', (_e, msg) => cb(msg)),
  act: (key, act) => ipcRenderer.send('popup-action', { key, act }),
  show: () => ipcRenderer.send('show-main'),
  shutdownIn: (secs) => ipcRenderer.send('shutdown-in', secs),
  cancelShutdown: () => ipcRenderer.send('shutdown-cancel'),
  mini: (d) => ipcRenderer.send('mini', d),
  onMiniData: (cb) => ipcRenderer.on('mini-data', (_e, d) => cb(d)),
  miniAct: (a) => ipcRenderer.send('mini-action', a),
  onMiniAction: (cb) => ipcRenderer.on('mini-action', (_e, a) => cb(a))
});
