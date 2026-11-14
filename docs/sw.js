// Ritmi (página pública): guarda la presentación y el cargador para que la app abra al instante aunque no haya internet.
// Tu Ritmi completa no pasa por aquí: el cargador la guarda aparte, solo con tu cuenta.
const CACHE = 'ritmi-pub-v1';
const SHELL = ['./', './index.html', './vendor/supabase.js', './icon.png', './manifest.json'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url); if (url.origin !== self.location.origin) return; // Supabase, letras, etc.: directo a internet
  if (/\/ritmi\//.test(url.pathname)) return; // la app de tareas tiene su propio guardado
  const page = req.mode === 'navigate' || /\/(index\.html)?$/.test(url.pathname);
  e.respondWith(caches.open(CACHE).then(async c => {
    if (page) { try { const r = await fetch(req, { cache: 'no-cache' }); if (r && r.ok) c.put(req, r.clone()); return r; } catch (x) { return (await c.match(req, { ignoreSearch: true })) || (await c.match('./index.html')) || Response.error(); } }
    const hit = await c.match(req, { ignoreSearch: true });
    const net = fetch(req).then(r => { if (r && r.ok) c.put(req, r.clone()); return r; }).catch(() => hit);
    return hit || net;
  }));
});
