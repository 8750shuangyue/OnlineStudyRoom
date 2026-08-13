import { useEffect } from 'react'
import { api } from './api.js'
import { notify } from './useAmbient.js'
import { getSettings } from './settings.js'
import { schedulePolling } from './poll.js'

const KEY = 'last_activity_seen'
const ICONS = { FOCUS_DONE: '⏱️', BADGE_EARNED: '🏅', FRIEND_ACCEPTED: '🤝', ROOM_CREATED: '🏠' }

/**
 * 好友动态推送：每 60 秒拉取一次信息流，有新动态且开启通知时弹浏览器通知。
 */
export function useActivityNotifications() {
  useEffect(() => {
    let busy = false
    const check = async () => {
      if (busy) {
        return
      }
      busy = true
      try {
        const feed = await api('/api/feed')
        const seen = Number(localStorage.getItem(KEY) || 0)
        const fresh = feed.filter((a) => a.id > seen)
        if (fresh.length > 0) {
          localStorage.setItem(KEY, String(fresh[0].id))
          if (getSettings().notifications) {
            fresh.slice(0, 3).forEach((a) => {
              notify(`${ICONS[a.type] || '✨'} ${a.username} ${a.text}`, a.text)
            })
          }
        }
      } catch {
        // 静默
      } finally {
        busy = false
      }
    }
    return schedulePolling(check, 60000)
  }, [])
}
