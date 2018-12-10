var CACHE = "flywithme";

self.addEventListener('fetch', (e) => {
  e.respondWith(fromCache(e.request).then((response) => {
    return response || fromBackend(e.request);
  }));
  e.waitUntil(fromBackend(e.request).then(refresh));
});

function fromCache(request) {
  return caches.open(CACHE).then((cache) => {
    return cache.match(request);
  });
}

function fromBackend(request) {
  return caches.open(CACHE).then((cache) => {
    return fetch(request).then((response) => {
      return cache.put(request, response.clone()).then(() => {
        return response;
      });
    });
  });
}

function refresh(response) {
  return self.clients.matchAll().then((clients) => {
      return clients.forEach((client) => {
        var message = {
          type: "refresh",
          url: response.url,
          eTag: response.headers.get("ETag")
        };
        client.postMessage(JSON.stringify(message));
        return response;
      });
  });
}
