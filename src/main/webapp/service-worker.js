var CACHE = "flywithme";


self.addEventListener('install', function(e) {
  self.skipWaiting();
});

self.addEventListener('activate', function(e) {
  self.registration.unregister()
    .then(function() {
      return self.clients.matchAll();
    })
    .then(function(clients) {
      clients.forEach(client => client.navigate(client.url))
    });
});


/*
self.addEventListener("fetch", (e) => {
  console.log("fetch", e);
  if (e.request.url.indexOf(".googleapis.com") !== -1) {
    return;
  }
  e.respondWith(fromCache(e.request).then((response) => {
    return response || fromBackend(e.request);
  }));
  e.waitUntil(fromBackend(e.request).then(refresh));
});

function fromCache(request) {
  console.log("fromCache", response);
  return caches.open(CACHE).then((cache) => {
    return cache.match(request);
  });
}

function fromBackend(request) {
  console.log("fromBackend", request);
  return caches.open(CACHE).then((cache) => {
    return fetch(request).then((response) => {
      return cache.put(request, response.clone()).then(() => {
        return response;
      });
    });
  });
}

function refresh(response) {
  console.log("refresh", response);
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
*/
