import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { getSettings, saveSettings } from '../settings.js'
import { setTheme } from '../theme.js'

function base64UrlToUint8Array(base64Url) {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4)
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  return Uint8Array.from([...raw].map((c) => c.charCodeAt(0)))
}

function arrayToBase64(array) {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < array.length; i += chunk) {
    binary += String.fromCharCode(...array.subarray(i, i + chunk))
  }
  return btoa(binary)
}

export default function SettingsPage() {
  const initial = getSettings()
  const [form, setForm] = useState(initial)
  const [saved, setSaved] = useState(false)
  const [backupInfo, setBackupInfo] = useState('')
  const [backupBusy, setBackupBusy] = useState(false)
  const [pushEnabled, setPushEnabled] = useState(false)
  const [pushBusy, setPushBusy] = useState(false)
  const [pushInfo, setPushInfo] = useState('')

  useEffect(() => {
    async function checkPush() {
      try {
        if ('serviceWorker' in navigator && 'PushManager' in window) {
          const reg = await navigator.serviceWorker.ready
          const sub = await reg.pushManager.getSubscription()
          setPushEnabled(!!sub)
        }
      } catch {
        // 开发模式未注册 Service Worker，忽略
      }
    }
    checkPush()
  }, [])

  function save(e) {
    e.preventDefault()
    saveSettings({
      ...form,
      focusMinutes: Math.max(0, Math.min(180, Number(form.focusMinutes) || 0)),
      breakMinutes: Math.max(1, Math.min(60, Number(form.breakMinutes) || 5)),
      autoRounds: Math.max(0, Math.min(20, Number(form.autoRounds) || 0))
    })
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  async function doBackup() {
    setBackupBusy(true)
    setBackupInfo('')
    try {
      const data = await api('/api/backup', { method: 'POST' })
      const kb = Math.max(1, Math.round(data.size / 1024))
      setBackupInfo(`✓ 已备份（${kb} KB）：${data.path}`)
    } catch (err) {
      setBackupInfo(`备份失败：${err.message}`)
    } finally {
      setBackupBusy(false)
    }
  }

  async function enablePush() {
    setPushBusy(true)
    setPushInfo('')
    try {
      if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
        setPushInfo('当前浏览器不支持推送通知')
        return
      }
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        setPushInfo('未获得通知权限')
        return
      }
      const reg = await navigator.serviceWorker.ready
      const { publicKey } = await api('/api/push/vapid-key')
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: base64UrlToUint8Array(publicKey)
      })
      await api('/api/push/subscribe', {
        method: 'POST',
        body: {
          endpoint: sub.endpoint,
          p256dh: arrayToBase64(new Uint8Array(sub.getKey('p256dh'))),
          auth: arrayToBase64(new Uint8Array(sub.getKey('auth')))
        }
      })
      setPushEnabled(true)
      setPushInfo('✅ 推送通知已开启')
    } catch (err) {
      setPushInfo(`开启失败：${err.message}`)
    } finally {
      setPushBusy(false)
    }
  }

  async function disablePush() {
    setPushBusy(true)
    setPushInfo('')
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        try {
          await api('/api/push/unsubscribe', { method: 'POST', body: { endpoint: sub.endpoint } })
        } catch {
          // 后端清理失败不阻塞本地退订
        }
        await sub.unsubscribe()
      }
      setPushEnabled(false)
      setPushInfo('已关闭推送通知')
    } catch (err) {
      setPushInfo(`关闭失败：${err.message}`)
    } finally {
      setPushBusy(false)
    }
  }

  return (
    <div>
      <h2>设置</h2>
      <div className="card settings-card">
        <h3>🔔 推送通知（Web Push）</h3>
        <p className="muted">
          浏览器后台也能收到提醒（专注完成、好友上线）。需要在 HTTPS 或 localhost 下使用。
        </p>
        <div className="row">
          {pushEnabled ? (
            <button className="btn secondary" onClick={disablePush} disabled={pushBusy}>
              {pushBusy ? '处理中...' : '关闭推送'}
            </button>
          ) : (
            <button className="btn" onClick={enablePush} disabled={pushBusy}>
              {pushBusy ? '处理中...' : '开启推送'}
            </button>
          )}
          {pushInfo && <span className="muted">{pushInfo}</span>}
        </div>
      </div>

      <div className="card settings-card">
        <h3>💾 数据备份</h3>
        <p className="muted">
          生成一份当前数据库的完整快照（H2 在线备份），保存在项目 backups 目录，自动保留最近 14 份。
        </p>
        <div className="row">
          <button className="btn secondary" onClick={doBackup} disabled={backupBusy}>
            {backupBusy ? '备份中...' : '备份数据'}
          </button>
          {backupInfo && (
            <span className={backupInfo.startsWith('✓') ? 'ok' : 'error'}>{backupInfo}</span>
          )}
        </div>
      </div>

      <div className="card settings-card">
        <form className="modal-form" onSubmit={save}>
          <label>
            单次专注时长（分钟，0 = 手动结束）
            <input
              type="number"
              min="0"
              max="180"
              value={form.focusMinutes}
              onChange={(e) => setForm({ ...form, focusMinutes: e.target.value })}
            />
            <span className="muted">设为 25/45/60 等，到时自动结束；0 表示自己手动结束</span>
          </label>

          <label>
            专注结束后休息时长（分钟）
            <input
              type="number"
              min="1"
              max="60"
              value={form.breakMinutes}
              onChange={(e) => setForm({ ...form, breakMinutes: e.target.value })}
            />
            <span className="muted">默认 5 分钟，范围 1-60</span>
          </label>

          <label>
            自动轮数（0 = 不自动开始下一轮）
            <input
              type="number"
              min="0"
              max="20"
              value={form.autoRounds}
              onChange={(e) => setForm({ ...form, autoRounds: e.target.value })}
            />
            <span className="muted">休息结束后自动开始下一轮专注，最多 20 轮</span>
          </label>

          <label>
            主题
            <select
              value={form.theme}
              onChange={(e) => {
                setTheme(e.target.value)
                setForm({ ...form, theme: e.target.value })
                setSaved(true)
                setTimeout(() => setSaved(false), 1500)
              }}
            >
              <option value="dark">🌙 深色</option>
              <option value="light">☀️ 浅色</option>
              <option value="system">🖥 跟随系统</option>
            </select>
          </label>

          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.notifications}
              onChange={(e) => setForm({ ...form, notifications: e.target.checked })}
            />
            浏览器通知（专注完成、被 @提及、休息结束）
          </label>

          <label>
            默认白噪音
            <select
              value={form.noiseType}
              onChange={(e) => setForm({ ...form, noiseType: e.target.value })}
            >
              <option value="brown">🌧 雨声</option>
              <option value="white">🗣 白噪</option>
              <option value="pink">🌸 粉噪</option>
            </select>
          </label>

          <label>
            白噪音音量（{Math.round(form.noiseVolume * 100)}%）
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={form.noiseVolume}
              onChange={(e) => setForm({ ...form, noiseVolume: Number(e.target.value) })}
              className="volume-slider"
            />
          </label>

          <div className="modal-actions">
            <button type="submit">保存设置</button>
            {saved && <span className="ok">✓ 已保存</span>}
          </div>
        </form>
      </div>
    </div>
  )
}
