import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { clearMentions } from '../useMessages.js'

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export default function MessageCenterPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [mentions, setMentions] = useState([])
  const [invites, setInvites] = useState([])
  const [friendRequests, setFriendRequests] = useState([])
  const [unreadRooms, setUnreadRooms] = useState([])
  const [briefs, setBriefs] = useState([])
  const [briefBusy, setBriefBusy] = useState(false)
  const [announcements, setAnnouncements] = useState([])
  const [annForm, setAnnForm] = useState({ title: '', content: '' })
  const [annBusy, setAnnBusy] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [m, inv, fr, ur, ann] = await Promise.all([
        api('/api/notifications?limit=50'),
        api('/api/invites'),
        api('/api/friends/requests'),
        api('/api/rooms/unread'),
        api('/api/announcements')
      ])
      setAnnouncements(ann)
      setMentions(m)
      setBriefs(m.filter((n) => n.type === 'DAILY_BRIEF' || n.type === 'WEEKLY_REPORT'))
      setInvites(inv)
      setFriendRequests(fr)
      setUnreadRooms(ur)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function openMention(item) {
    try {
      await api(`/api/notifications/${item.id}/read`, { method: 'POST' })
    } catch {
      // 即使标记失败也照常跳转
    }
    clearMentions()
    setMentions((prev) => prev.map((n) => (n.id === item.id ? { ...n, read: true } : n)))
    if (item.roomId) {
      navigate(`/rooms/${item.roomId}`)
    }
  }

  async function markAllRead() {
    setBusy(true)
    setError('')
    try {
      await api('/api/notifications/read-all', { method: 'POST' })
      clearMentions()
      setMentions((prev) => prev.map((n) => ({ ...n, read: true })))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function actInvite(id, action) {
    setBusy(true)
    setError('')
    try {
      await api(`/api/invites/${id}/${action}`, { method: 'POST' })
      setInvites((prev) => prev.filter((i) => i.id !== id))
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function actFriend(id, action) {
    setBusy(true)
    setError('')
    try {
      await api(`/api/friends/requests/${id}/${action}`, { method: 'POST' })
      setFriendRequests((prev) => prev.filter((r) => r.id !== id))
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function generateBrief() {
    setBriefBusy(true)
    setError('')
    try {
      await api('/api/ai/daily-brief', { method: 'POST' })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBriefBusy(false)
    }
  }

  async function publishAnnouncement(e) {
    e.preventDefault()
    setAnnBusy(true)
    setError('')
    try {
      await api('/api/announcements/admin', {
        method: 'POST',
        body: { title: annForm.title, content: annForm.content }
      })
      setAnnForm({ title: '', content: '' })
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setAnnBusy(false)
    }
  }

  return (
    <div>
      <h2>消息中心</h2>
      {error && <p className="error">{error}</p>}

      <div className="card">
        <h3>📢 公告</h3>
        {announcements.length === 0 ? (
          <p className="muted">暂无公告</p>
        ) : (
          <div className="msg-list">
            {announcements.map((a) => (
              <div className="msg-item" key={a.id}>
                <div className="msg-title">
                  📢 {a.title}
                  <span className="msg-time">{formatTime(a.createdAt)}</span>
                </div>
                <div className="msg-body">{a.content}</div>
              </div>
            ))}
          </div>
        )}
        {user?.admin && (
          <form className="modal-form" onSubmit={publishAnnouncement}>
            <input
              placeholder="公告标题"
              value={annForm.title}
              onChange={(e) => setAnnForm({ ...annForm, title: e.target.value })}
              maxLength={200}
            />
            <textarea
              placeholder="公告内容"
              value={annForm.content}
              onChange={(e) => setAnnForm({ ...annForm, content: e.target.value })}
              maxLength={2000}
              rows={3}
            />
            <button
              disabled={annBusy || !annForm.title.trim() || !annForm.content.trim()}
            >
              {annBusy ? '发布中...' : '发布公告'}
            </button>
          </form>
        )}
      </div>

      <div className="card">
        <div className="row-between">
          <h3>📋 每日简报</h3>
          <button className="btn tiny secondary" onClick={generateBrief} disabled={briefBusy}>
            {briefBusy ? '生成中...' : '生成今日简报'}
          </button>
        </div>
        {briefs.length === 0 ? (
          <p className="muted">还没有简报，点「生成今日简报」让 AI 帮你复盘昨天、规划今天。</p>
        ) : (
          <div className="msg-list">
            {briefs.slice(0, 3).map((b) => (
              <div className="msg-item" key={b.id}>
                <div className="msg-title">
                  {b.type === 'WEEKLY_REPORT' ? '📊' : '📋'} {b.title}
                  <span className="msg-time">{formatTime(b.createdAt)}</span>
                </div>
                <div className="msg-body">{b.body}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <div className="row-between">
          <h3>
            📣 @提及
            {mentions.filter((n) => !n.read).length > 0 && (
              <span className="unread-badge">{mentions.filter((n) => !n.read).length}</span>
            )}
          </h3>
          {mentions.some((n) => !n.read) && (
            <button className="btn tiny secondary" onClick={markAllRead} disabled={busy}>
              全部已读
            </button>
          )}
        </div>
        {mentions.length === 0 ? (
          <p className="muted">还没有人 @ 你。</p>
        ) : (
          <div className="msg-list">
            {mentions.map((n) => (
              <div
                className={`msg-item ${n.read ? 'read' : ''}`}
                key={n.id}
                onClick={() => openMention(n)}
              >
                <div className="msg-title">
                  {!n.read && <span className="msg-dot" />}
                  {n.title}
                  <span className="msg-time">{formatTime(n.createdAt)}</span>
                </div>
                <div className="msg-body">{n.body}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h3>
          🚪 房间邀请
          {invites.length > 0 && <span className="unread-badge">{invites.length}</span>}
        </h3>
        {invites.length === 0 ? (
          <p className="muted">没有待处理的房间邀请。</p>
        ) : (
          <div className="msg-list">
            {invites.map((inv) => (
              <div className="msg-item" key={inv.id}>
                <div className="msg-title">
                  {inv.fromUsername} 邀请你加入「{inv.roomName}」
                  <span className="msg-time">{formatTime(inv.createdAt)}</span>
                </div>
                <div className="row msg-actions">
                  <button
                    className="btn tiny"
                    onClick={() => actInvite(inv.id, 'accept')}
                    disabled={busy}
                  >
                    接受
                  </button>
                  <button
                    className="btn tiny secondary"
                    onClick={() => actInvite(inv.id, 'reject')}
                    disabled={busy}
                  >
                    拒绝
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h3>
          👥 好友请求
          {friendRequests.length > 0 && (
            <span className="unread-badge">{friendRequests.length}</span>
          )}
        </h3>
        {friendRequests.length === 0 ? (
          <p className="muted">没有待处理的好友请求。</p>
        ) : (
          <div className="msg-list">
            {friendRequests.map((r) => (
              <div className="msg-item" key={r.id}>
                <div className="msg-title">
                  {r.username} 请求添加你为好友
                  <span className="msg-time">{formatTime(r.createdAt)}</span>
                </div>
                <div className="row msg-actions">
                  <button
                    className="btn tiny"
                    onClick={() => actFriend(r.id, 'accept')}
                    disabled={busy}
                  >
                    接受
                  </button>
                  <button
                    className="btn tiny secondary"
                    onClick={() => actFriend(r.id, 'reject')}
                    disabled={busy}
                  >
                    拒绝
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h3>
          💬 房间未读
          {unreadRooms.length > 0 && <span className="unread-badge">{unreadRooms.length}</span>}
        </h3>
        {unreadRooms.length === 0 ? (
          <p className="muted">所有房间都已读。</p>
        ) : (
          <div className="msg-list">
            {unreadRooms.map((room) => (
              <div
                className="msg-item"
                key={room.roomId}
                onClick={() => navigate(`/rooms/${room.roomId}`)}
              >
                <div className="msg-title">
                  房间 #{room.roomId} 有 {room.count} 条未读消息
                </div>
                <button className="btn tiny secondary">进入查看</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
