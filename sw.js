// Prumo — Service Worker v2.0
const CACHE_NAME = 'prumo-v2.2';
const ASSETS = [
    './',
    './index.html',
    './icon.svg'
];

const CDN_HOSTS = [
    'cdn.tailwindcss.com',
    'fonts.googleapis.com',
    'fonts.gstatic.com',
    'www.gstatic.com'
];

// Install — cache essential assets
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            return cache.addAll(ASSETS).catch(err => {
                console.warn('[SW] Falha ao cachear alguns assets:', err);
            });
        })
    );
    self.skipWaiting();
});

// Activate — clean old caches
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(
                keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
            )
        )
    );
    self.clients.claim();
});

function isCDN(url) {
    try {
        const host = new URL(url).hostname;
        return CDN_HOSTS.some(h => host === h || host.endsWith('.' + h));
    } catch(e) { return false; }
}

// Fetch handler
self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;

    // CDN assets: stale-while-revalidate (serve from cache instantly, update in background)
    if (isCDN(event.request.url)) {
        event.respondWith(
            caches.open(CACHE_NAME).then(cache =>
                cache.match(event.request).then(cached => {
                    const fetched = fetch(event.request).then(response => {
                        if (response && response.status === 200) {
                            cache.put(event.request, response.clone());
                        }
                        return response;
                    }).catch(() => cached);
                    return cached || fetched;
                })
            )
        );
        return;
    }

    // Navigation requests: network first, fallback to cache
    if (event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request)
                .then(response => {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                    return response;
                })
                .catch(() => caches.match(event.request) || caches.match('./index.html'))
        );
        return;
    }

    // Other same-origin requests: network first then cache
    event.respondWith(
        fetch(event.request)
            .then(response => {
                if (response && response.status === 200) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            })
            .catch(() => caches.match(event.request))
    );
});
