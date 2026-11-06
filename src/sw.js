// Ritmo: guarda la app en el celular para que abra al instante,
// aunque el internet esté lento. Se actualiza sola en segundo plano.
const CACHE = 'ritmo-v1';
const SHELL = ['./', './index.html', './vendor/supabase.js', './icon.png', './manifest.json'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(self.clients.claim()); });
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const mine = url.origin === self.location.origin;
  const fonts = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (!mine && !fonts) return; // Supabase y lo demás van directo a internet
  e.respondWith(caches.open(CACHE).then(async c => {
    const hit = await c.match(req, { ignoreSearch: mine });
    const net = fetch(req).then(r => { if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone()); return r; }).catch(() => hit);
    return hit || net; // primero lo guardado; en paralelo trae lo nuevo para la próxima vez
  }));
});
