const CACHE = 'eisbad-v1';
const CORE = ['./', 'index.html', 'app.js', 'style.css', 'manifest.webmanifest', 'phrases.json',
  'icons/icon-192.png', 'icons/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    await c.addAll(CORE);
    try {
      const p = await (await fetch('phrases.json')).json();
      const urls = [];
      for (const k of Object.keys(p)) for (const l of ['de', 'en']) urls.push(`voice/${l}/${k}.mp3`);
      await Promise.all(urls.map((u) => c.add(u).catch(() => {})));
    } catch (err) { /* offline-Cache unvollständig, wird beim Nutzen nachgeladen */ }
    self.skipWaiting();
  })());
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});

async function rangeResponse(res, range) {
  const buf = await res.arrayBuffer();
  const m = /bytes=(\d+)-(\d*)/.exec(range);
  if (!m) return res;
  const start = Number(m[1]);
  const end = m[2] ? Math.min(Number(m[2]), buf.byteLength - 1) : buf.byteLength - 1;
  return new Response(buf.slice(start, end + 1), {
    status: 206,
    headers: {
      'Content-Type': res.headers.get('Content-Type') || 'audio/mpeg',
      'Content-Range': `bytes ${start}-${end}/${buf.byteLength}`,
      'Content-Length': String(end - start + 1),
    },
  });
}

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith((async () => {
    const hit = await caches.match(req, { ignoreVary: true });
    if (hit) {
      const range = req.headers.get('range');
      if (!/\.mp3$/.test(req.url)) {
        // App-Dateien: aus dem Cache, im Hintergrund aktualisieren
        fetch(req).then(async (r) => { if (r.status === 200) (await caches.open(CACHE)).put(req, r); }).catch(() => {});
      }
      return range ? rangeResponse(hit, range) : hit;
    }
    try {
      const res = await fetch(req);
      if (res.status === 200) (await caches.open(CACHE)).put(req, res.clone());
      return res;
    } catch (err) {
      return (await caches.match('index.html')) || Response.error();
    }
  })());
});
