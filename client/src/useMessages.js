import { useSyncExternalStore } from 'react'
import { api } from './api.js'
import { schedulePolling } from './poll.js'

/**
 * 全局消息角标状态：@提及 / 房间邀请 / 好友请求 计数。
 */
let counts = { mentions: 0, invites: 0, friends: 0 }
let started = false
const listeners = new Set()

function emit() {
  listeners.forEach((fn) => fn())
}

async function refresh() {
  try {
    const [m, inv, fr] = await Promise.all([
      api('/api/notifications/unread-count'),
      api('/api/invites'),
      api('/api/friends/requests')
    ])
    const next = {
      mentions: m.count || 0,
      invites: inv.length || 0,
      friends: fr.length || 0
    }
    if (JSON.stringify(next) !== JSON.stringify(counts)) {
      counts = next
      emit()
    }
  } catch {
    // 静默
  }
}

function ensureStarted() {
  if (started) {
    return
  }
  started = true
  schedulePolling(refresh, 30000)
}

export function clearMentions() {
  if (counts.mentions === 0) {
    return
  }
  counts = { ...counts, mentions: 0 }
  emit()
}

export function useMessages() {
  ensureStarted()
  const state = useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    () => counts
  )
  const total = state.mentions + state.invites + state.friends
  return { counts: state, total }
}
