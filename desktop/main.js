// Ritmo para Windows: la misma app de Ritmo, con bandeja del sistema,
// inicio automático con Windows y ventanas de aviso encima de todo.
const { app, BrowserWindow, Tray, Menu, ipcMain, nativeImage, screen, shell } = require('electron');
const path = require('path');
const { exec } = require('child_process');

const APP_URL = 'https://andresg17841.github.io/ritmo/';
let win = null, tray = null, popup = null, quitting = false;
const startHidden = process.argv.includes('--oculto');

if (!app.requestSingleInstanceLock()) { app.quit(); }
app.on('second-instance', () => showMain());

function icon(size) {
  return nativeImage.createFromPath(path.join(__dirname, 'icon.png')).resize({ width: size, height: size });
}

function createMain() {
  win = new BrowserWindow({
    width: 480, height: 860, minWidth: 380, minHeight: 600,
    title: 'Ritmo', icon: icon(256), show: !startHidden, autoHideMenuBar: true,
    backgroundColor: '#0E1412',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, backgroundThrottling: false }
  });
  // Carga la app publicada (así cada mejora llega sola); si no hay internet, usa la copia local.
  const local = () => win.loadFile(path.join(__dirname, 'web', 'index.html'));
  win.webContents.once('did-fail-load', local);
  win.loadURL(APP_URL).catch(local);
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  win.on('close', (e) => { if (!quitting) { e.preventDefault(); win.hide(); } });
}

function showMain() { if (!win) return; win.show(); if (win.isMinimized()) win.restore(); win.focus(); }

function createTray() {
  tray = new Tray(icon(16));
  tray.setToolTip('Ritmo');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Abrir Ritmo', click: showMain },
    { type: 'separator' },
    { label: 'Iniciar con Windows', type: 'checkbox', checked: app.getLoginItemSettings().openAtLogin,
      click: (m) => app.setLoginItemSettings({ openAtLogin: m.checked, args: ['--oculto'] }) },
    { label: 'Salir de Ritmo', click: () => { quitting = true; app.quit(); } }
  ]));
  tray.on('click', showMain);
}

function showPopup(data) {
  if (popup && !popup.isDestroyed()) { popup.__done = true; popup.close(); }
  const area = screen.getPrimaryDisplay().workArea;
  const w = data.big ? 520 : 380, h = data.big ? 300 : 200;
  const x = data.big ? Math.round(area.x + (area.width - w) / 2) : area.x + area.width - w - 16;
  const y = data.big ? Math.round(area.y + (area.height - h) / 2.4) : area.y + area.height - h - 16;
  const p = new BrowserWindow({
    width: w, height: h, x, y, frame: false, resizable: false, alwaysOnTop: true, skipTaskbar: false,
    show: false, title: 'Ritmo', icon: icon(256), backgroundColor: '#17201D', focusable: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  p.__done = false; popup = p;
  p.setAlwaysOnTop(true, data.big ? 'screen-saver' : 'floating');
  p.loadFile(path.join(__dirname, 'popup.html'), { hash: encodeURIComponent(JSON.stringify(data)) });
  p.once('ready-to-show', () => { if (data.big) { p.show(); p.focus(); p.flashFrame(true); } else p.showInactive(); });
  p.on('closed', () => { if (!p.__done && win) win.webContents.send('alert-action', { key: data.key, act: 'ok' }); if (popup === p) popup = null; });
}

ipcMain.on('popup', (_e, data) => showPopup(data));
ipcMain.on('popup-action', (_e, msg) => {
  if (win) win.webContents.send('alert-action', msg);
  if (popup && !popup.isDestroyed()) { popup.__done = true; popup.close(); }
});
ipcMain.on('show-main', () => showMain());
ipcMain.on('shutdown-in', (_e, secs) => {
  const s = Math.max(60, Math.min(86400, parseInt(secs, 10) || 1800));
  exec(`shutdown /s /t ${s} /c "Ritmo: hora de dormir. Para cancelar, abre Ritmo > Ajustes > Cancelar apagado."`);
});
ipcMain.on('shutdown-cancel', () => exec('shutdown /a'));

app.whenReady().then(() => {
  app.setAppUserModelId('co.ritmo.app');
  if (!app.getLoginItemSettings().openAtLogin && !require('fs').existsSync(path.join(app.getPath('userData'), '.inicio-configurado'))) {
    app.setLoginItemSettings({ openAtLogin: true, args: ['--oculto'] });
    require('fs').writeFileSync(path.join(app.getPath('userData'), '.inicio-configurado'), '1');
  }
  createMain();
  createTray();
});
app.on('before-quit', () => { quitting = true; });
app.on('window-all-closed', (e) => { /* queda en la bandeja */ });
