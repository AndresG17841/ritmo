// Ritmo: guarda la app en el celular para que abra al instante,
// aunque el internet esté lento. Se actualiza sola en segundo plano.
const CACHE = 'ritmo-v2';
const SHELL = ['./', './index.html', './vendor/supabase.js', './icon.png', './manifest.json'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const mine = url.origin === self.location.origin;
  const fonts = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (!mine && !fonts) return; // Supabase y lo demás van directo a internet
  const page = mine && (req.mode === 'navigate' || /\/(index\.html)?$/.test(url.pathname));
  if (page) { // la app misma: primero lo nuevo (máx. 3 s); si el internet está malo, lo guardado
    e.respondWith(caches.open(CACHE).then(async c => {
      const net = fetch(req, { cache: 'no-cache' }).then(r => { if (r && r.ok) c.put(req, r.clone()); return r; });
      const slow = new Promise(ok => setTimeout(ok, 3000)).then(() => c.match(req, { ignoreSearch: true }));
      try { return (await Promise.race([net, slow])) || await net; } catch (x) { return (await c.match(req, { ignoreSearch: true })) || Response.error(); }
    }));
    return;
  }
  e.respondWith(caches.open(CACHE).then(async c => {
    const hit = await c.match(req, { ignoreSearch: mine });
    const net = fetch(req).then(r => { if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone()); return r; }).catch(() => hit);
    return hit || net; // primero lo guardado; en paralelo trae lo nuevo para la próxima vez
  }));
});
