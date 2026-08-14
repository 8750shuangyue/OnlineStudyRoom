/* 自习室 PWA Service Worker：预缓存应用壳 + 运行时缓存静态资源 + 离线回退 */
/* global clients */
const CACHE_NAME = 'study-room-v1'
const PRECACHE_URLS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/icon-192.png',
  '/icon-512.png',
  '/maskable-512.png'
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
      )
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') {
    return
  }
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) {
    return
  }
  // API 与 WebSocket 走网络，不缓存
  if (url.pathname.startsWith('/api') || url.pathname.startsWith('/ws')) {
    return
  }

  // 页面导航：网络优先，失败回退缓存的首页（可离线打开）
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put('/index.html', copy))
          return response
        })
        .catch(() => caches.match('/index.html'))
    )
    return
  }

  // 静态资源（带 hash 的 JS/CSS、图标等）：缓存优先，未命中时回源并写入缓存
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) {
        return cached
      }
      return fetch(request).then((response) => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy))
        }
        return response
      })
    })
  )
})

/* ---------- Web Push：收到推送时显示通知 ---------- */
self.addEventListener('push', (event) => {
  let data = { title: '自习室', body: '', url: '/' }
  try {
    data = { ...data, ...(event.data ? event.data.json() : {}) }
  } catch {
    if (event.data) data.body = event.data.text()
  }
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      data: { url: data.url }
    })
  )
})

/* 点击通知：聚焦已有窗口或打开目标页 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/'
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if ('focus' in client) {
          if ('navigate' in client && url) {
            client.navigate(url).catch(() => {})
          }
          return client.focus()
        }
      }
      return clients.openWindow(url)
    })
  )
})
