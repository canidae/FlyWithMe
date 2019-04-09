var CACHE = "flywithme";

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(cache => {
    return cache.addAll([
      "/flywithme.css",
      "/flywithme.js",
      "/index.html",
      "/icon.png",
      "/manifest.json",
      "/images/GoogleMaps.svg",
      "/images/logo.png",
      "/images/navigate.svg",
      "/images/NOAA.svg",
      "/libs/google_maps_v3/1.png",
      "/libs/google_maps_v3/2.png",
      "/libs/google_maps_v3/3.png",
      "/libs/google_maps_v3/4.png",
      "/libs/google_maps_v3/5.png",
      "/libs/google_maps_v3/markerclusterer.js",
      "/libs/idb-keyval_3.2.0/idb-keyval-iife.min.js",
      "/libs/mithril-3.0.1/mithril.min.js"
    ]);
  }));
});

self.addEventListener('fetch', e => {
  e.respondWith(fetch(e.request) || caches.match(e.request).then(response => {
    return response;
  }));
});
