import { useSyncExternalStore } from 'react'
import { api } from './api.js'

/**
 * 全局未读状态：Layout / RoomsPage / RoomPage 共享一份数据。
 * 每 30s 轮询 + 页面回到前台时刷新；进入房间后调用 markRoomRead 立即清零。
 */
let unreads = {}
let started = false
const listeners = new Set()

function emit() {
  listeners.forEach((fn) => fn())
}

async function refresh() {
  try {
    const data = await api('/api/rooms/unread')
    const next = Object.fromEntries(data.map((d) => [String(d.roomId), d.count]))
    if (JSON.stringify(next) !== JSON.stringify(unreads)) {
      unreads = next
      emit()
    }
  } catch {
    // 未登录或接口暂不可用时静默
  }
}

function ensureStarted() {
  if (started) {
    return
  }
  started = true
  refresh()
  setInterval(refresh, 30000)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      refresh()
    }
  })
}

export function markRoomRead(roomId) {
  if (unreads[String(roomId)] === undefined) {
    return
  }
  unreads = { ...unreads }
  delete unreads[String(roomId)]
  emit()
}

export function useUnread() {
  ensureStarted()
  const state = useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    () => unreads
  )
  const total = Object.values(state).reduce((sum, count) => sum + count, 0)
  return { unreads: state, total }
}
