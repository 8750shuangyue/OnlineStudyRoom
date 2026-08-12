import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useStomp } from '../useStomp.js'
import { markRoomRead } from '../useUnread.js'
import { getSettings } from '../settings.js'
import Modal from '../components/Modal.jsx'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import {
  notify,
  playBeep,
  requestNotifyPermission,
  usePageTitleCountdown,
  useWhiteNoise
} from '../useAmbient.js'

function formatSeconds(total) {
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export default function RoomPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [room, setRoom] = useState(null)
  const [messages, setMessages] = useState([])
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [input, setInput] = useState('')
  const [mentionOpen, setMentionOpen] = useState(false)
  const [mentionList, setMentionList] = useState([])
  const [mentionToast, setMentionToast] = useState(null)
  const [immersive, setImmersive] = useState(false)
  const [tutorMode, setTutorMode] = useState(false)
  const [tutorBusy, setTutorBusy] = useState(false)
  const inputRef = useRef(null)
  const autoActionRef = useRef(false)
  const [online, setOnline] = useState(0)
  const [leaderboard, setLeaderboard] = useState([])
  const [boardPeriod, setBoardPeriod] = useState('all')
  const [session, setSession] = useState(null)
  const [elapsed, setElapsed] = useState(0)
  const [summary, setSummary] = useState(null)
  const [showEdit, setShowEdit] = useState(false)
  const [editForm, setEditForm] = useState({
    name: '',
    category: '',
    announcement: '',
    password: '',
    focusMinutes: '',
    breakMinutes: '',
    aiTutorEnabled: false,
    tutorPersona: ''
  })
  const [editClearPassword, setEditClearPassword] = useState(false)
  const [showTransfer, setShowTransfer] = useState(false)
  const [transferTarget, setTransferTarget] = useState('')
  const [showJoinPassword, setShowJoinPassword] = useState(false)
  const [joinPassword, setJoinPassword] = useState('')
  const [focusMap, setFocusMap] = useState({})
  const [syncBanner, setSyncBanner] = useState(null)
  const [showInvite, setShowInvite] = useState(false)
  const [friends, setFriends] = useState([])
  const [inviteMsg, setInviteMsg] = useState('')
  const [breakLeft, setBreakLeft] = useState(null)
  const [breakActive, setBreakActive] = useState(false)
  const [breakDone, setBreakDone] = useState(false)
  const [shortNotice, setShortNotice] = useState(false)
  const [reflectionText, setReflectionText] = useState('')
  const [reflectionSaved, setReflectionSaved] = useState(false)
  const [rounds, setRounds] = useState(() => Number(localStorage.getItem(`rounds_${id}`) || 0))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const noise = useWhiteNoise()
  const settings = useMemo(() => getSettings(), [])
  const setTitle = usePageTitleCountdown()

  const roomFocusMinutes = room?.focusMinutes || settings.focusMinutes
  const roomBreakMinutes = room?.breakMinutes || settings.breakMinutes

  useEffect(() => {
    noise.setVolume(settings.noiseVolume)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const load = useCallback(async () => {
    try {
      const [roomData, page, onlineData, boardData, active, focusData] = await Promise.all([
        api(`/api/rooms/${id}`),
        api(`/api/rooms/${id}/messages?limit=50`),
        api(`/api/rooms/${id}/online`),
        api(`/api/rooms/${id}/leaderboard?period=${boardPeriod}`),
        api('/api/sessions/active'),
        api(`/api/rooms/${id}/focus-status`)
      ])
      setRoom(roomData)
      setMessages(page.messages || [])
      setHasMore(Boolean(page.hasMore))
      setOnline(onlineData.onlineCount)
      setLeaderboard(boardData)
      setSession(active)
      setFocusMap(Object.fromEntries(focusData.map((f) => [f.username, f])))
      if (roomData.members.includes(user.username)) {
        api(`/api/rooms/${id}/read`, { method: 'POST' }).catch(() => {})
        markRoomRead(id)
      }
    } catch (err) {
      setError(err.message)
    }
  }, [id, boardPeriod, user.username])

  useEffect(() => {
    load()
  }, [load])

  const { send } = useStomp(id, {
    username: user.username,
    onMessage: (msg) => setMessages((prev) => [...prev.filter((m) => m.id !== msg.id), msg]),
    onPresence: (p) => setOnline(p.onlineCount),
    onFocus: (e) =>
      setFocusMap((prev) => {
        const next = { ...prev }
        if (e.type === 'START') {
          next[e.username] = {
            username: e.username,
            sessionId: e.sessionId,
            startedAt: e.startedAt
          }
        } else {
          delete next[e.username]
        }
        return next
      }),
    onSync: (e) => setSyncBanner(e),
    onMention: (m) => {
      setMentionToast(m)
      if (settings.notifications) {
        notify(`${m.fromUsername} 在「${m.roomName || '房间'}」提到了你`, m.content)
      }
    }
  })

  const mentionPattern = useMemo(() => {
    if (!room || !room.members || room.members.length === 0) {
      return null
    }
    const names = [...room.members].sort((a, b) => b.length - a.length)
    return new RegExp(
      '(@(?:' + names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|') + '))',
      'g'
    )
  }, [room])

  useEffect(() => {
    if (!mentionToast) {
      return undefined
    }
    const timer = setTimeout(() => setMentionToast(null), 10000)
    return () => clearTimeout(timer)
  }, [mentionToast])

  useEffect(() => {
    if (!session) {
      setTitle('')
      return undefined
    }
    const started = new Date(session.startedAt).getTime()
    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - started) / 1000)))
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setTitle 每次渲染都是新函数
  }, [session])

  useEffect(() => {
    if (
      session?.status === 'ACTIVE' &&
      roomFocusMinutes > 0 &&
      elapsed >= roomFocusMinutes * 60 &&
      !autoActionRef.current
    ) {
      autoActionRef.current = true
      stopFocus()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elapsed, session, roomFocusMinutes])

  useEffect(() => {
    if (session?.status === 'ACTIVE') {
      setTitle(`⏱ ${formatSeconds(elapsed)}`)
    } else if (breakActive && breakLeft !== null) {
      setTitle(`☕ ${formatSeconds(breakLeft)}`)
    } else {
      setTitle('')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setTitle 每次渲染都是新函数
  }, [elapsed, breakLeft, breakActive, session])

  useEffect(() => {
    if (!breakActive || breakLeft === null) {
      return undefined
    }
    if (breakLeft <= 0) {
      setBreakActive(false)
      setBreakDone(true)
      playBeep(660, 0.4)
      if (settings.notifications) {
        notify('休息结束 ☕', '能量满格，开始下一轮吧！')
      }
      if (settings.autoRounds > 0 && rounds < settings.autoRounds) {
        startFocus()
      }
      return undefined
    }
    const timer = setTimeout(() => setBreakLeft((v) => v - 1), 1000)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- startFocus 每次渲染都是新函数
  }, [breakActive, breakLeft, settings.notifications, settings.autoRounds, rounds])

  const isMember = room && room.members.includes(user.username)
  const isOwner = room && room.ownerUsername === user.username

  async function joinRoom() {
    if (room.hasPassword) {
      setJoinPassword('')
      setShowJoinPassword(true)
      return
    }
    await doJoin(null)
  }

  async function doJoin(password) {
    setBusy(true)
    setError('')
    try {
      await api(`/api/rooms/${id}/join`, { method: 'POST', body: { password } })
      setShowJoinPassword(false)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function leaveRoom() {
    setError('')
    try {
      await api(`/api/rooms/${id}/leave`, { method: 'POST' })
      navigate('/rooms')
    } catch (err) {
      setError(err.message)
    }
  }

  async function saveEdit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api(`/api/rooms/${id}`, {
        method: 'PUT',
        body: {
          name: editForm.name,
          category: editForm.category,
          announcement: editForm.announcement,
          password: editClearPassword ? '' : editForm.password || null,
          focusMinutes: editForm.focusMinutes ? Number(editForm.focusMinutes) : 0,
          breakMinutes: editForm.breakMinutes ? Number(editForm.breakMinutes) : 0,
          aiTutorEnabled: editForm.aiTutorEnabled,
          tutorPersona: editForm.tutorPersona
        }
      })
      setShowEdit(false)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function transfer(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api(`/api/rooms/${id}/transfer`, { method: 'POST', body: { username: transferTarget } })
      setShowTransfer(false)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function deleteRoom() {
    if (!window.confirm(`确定解散「${room.name}」吗？解散后房间不可见（学习记录保留）。`)) {
      return
    }
    setError('')
    try {
      await api(`/api/rooms/${id}`, { method: 'DELETE' })
      navigate('/rooms')
    } catch (err) {
      setError(err.message)
    }
  }

  async function kick(username) {
    if (!window.confirm(`确定将 ${username} 移出房间吗？`)) {
      return
    }
    setError('')
    try {
      await api(`/api/rooms/${id}/kick`, { method: 'POST', body: { username } })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function startFocus() {
    requestNotifyPermission()
    setBusy(true)
    setError('')
    try {
      const s = await api('/api/sessions/start', { method: 'POST', body: { roomId: Number(id) } })
      autoActionRef.current = false
      setSession(s)
      setSummary(null)
      setReflectionText('')
      setReflectionSaved(false)
      setBreakActive(false)
      setBreakDone(false)
      setBreakLeft(null)
      setShortNotice(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function syncFocus() {
    setBusy(true)
    setError('')
    try {
      const s = await api('/api/sessions/sync-start', {
        method: 'POST',
        body: { roomId: Number(id) }
      })
      setSession(s)
      setFocusMap((prev) => ({
        ...prev,
        [user.username]: { username: user.username, sessionId: s.id, startedAt: s.startedAt }
      }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function openInvite() {
    setInviteMsg('')
    setError('')
    try {
      const data = await api('/api/friends')
      setFriends(data)
      setShowInvite(true)
    } catch (err) {
      setError(err.message)
    }
  }

  async function sendInvite(username) {
    setInviteMsg('')
    setError('')
    try {
      await api(`/api/rooms/${id}/invite`, { method: 'POST', body: { username } })
      setInviteMsg(`已邀请 ${username}`)
    } catch (err) {
      setError(err.message)
    }
  }

  async function stopFocus() {
    setBusy(true)
    setError('')
    try {
      const s = await api(`/api/sessions/${session.id}/stop`, { method: 'POST' })
      autoActionRef.current = false
      setSession(s)
      setReflectionText('')
      setReflectionSaved(false)
      const valid = (s.durationSeconds || 0) >= 900
      setShortNotice(!valid)
      if (valid) {
        playBeep(880, 0.4)
        if (settings.notifications) {
          notify('专注完成 🎉', `本次专注约 ${Math.round((s.durationSeconds || 0) / 60)} 分钟`)
        }
        const nextRounds = rounds + 1
        setRounds(nextRounds)
        localStorage.setItem(`rounds_${id}`, String(nextRounds))
        setBreakLeft(roomBreakMinutes * 60)
        setBreakActive(true)
        setBreakDone(false)
      } else {
        setBreakLeft(null)
        setBreakActive(false)
        setBreakDone(false)
      }
      load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function generateSummary() {
    setBusy(true)
    setError('')
    try {
      const data = await api(`/api/ai/sessions/${session.id}/summary`, { method: 'POST' })
      setSummary(data.summary)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function saveReflection() {
    setBusy(true)
    setError('')
    try {
      await api(`/api/sessions/${session.id}/reflection`, {
        method: 'PUT',
        body: { text: reflectionText }
      })
      setReflectionSaved(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function skipBreak() {
    setBreakActive(false)
    setBreakDone(false)
    setBreakLeft(null)
  }

  function sendMessage(e) {
    e.preventDefault()
    const content = input.trim()
    if (!content) {
      return
    }
    if (tutorMode) {
      sendTutor(content)
    } else {
      send(content)
    }
    setInput('')
    setMentionOpen(false)
  }

  async function sendTutor(content) {
    setTutorBusy(true)
    setError('')
    try {
      const data = await api(`/api/rooms/${id}/tutor`, {
        method: 'POST',
        body: { message: content }
      })
      setMessages((prev) => [
        ...prev,
        { id: Date.now(), username: '🤖 助教', content: data.reply }
      ])
    } catch (err) {
      setError(err.message)
    } finally {
      setTutorBusy(false)
    }
  }

  function onInputChange(value) {
    setInput(value)
    const at = value.lastIndexOf('@')
    const ws = Math.max(value.lastIndexOf(' '), value.lastIndexOf('\n'))
    if (at > ws && room) {
      const q = value.slice(at + 1)
      if (/^[A-Za-z0-9_\u4e00-\u9fa5]*$/.test(q)) {
        const list = room.members.filter(
          (m) => m !== user.username && m.toLowerCase().includes(q.toLowerCase())
        )
        setMentionList(list.slice(0, 8))
        setMentionOpen(list.length > 0)
        return
      }
    }
    setMentionOpen(false)
  }

  function pickMention(name) {
    const at = input.lastIndexOf('@')
    const before = input.slice(0, at)
    const after = input.slice(at + 1).replace(/^[A-Za-z0-9_\u4e00-\u9fa5]*/, '')
    setInput(before + '@' + name + ' ' + after)
    setMentionOpen(false)
    inputRef.current?.focus()
  }

  async function loadOlder() {
    if (loadingOlder || messages.length === 0) {
      return
    }
    setLoadingOlder(true)
    try {
      const page = await api(`/api/rooms/${id}/messages?before=${messages[0].id}&limit=50`)
      setMessages((prev) => [...(page.messages || []), ...prev])
      setHasMore(Boolean(page.hasMore))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingOlder(false)
    }
  }

  function renderContent(content) {
    if (!mentionPattern) {
      return content
    }
    return content.split(mentionPattern).map((part, i) => {
      if (part.startsWith('@') && room.members.includes(part.slice(1))) {
        return (
          <span className="mention" key={i}>
            {part}
          </span>
        )
      }
      return <span key={i}>{part}</span>
    })
  }

  if (!room) {
    return error ? (
      <div className="center">
        <p className="error">加载失败：{error}</p>
        <button className="btn" onClick={load}>
          重试
        </button>
      </div>
    ) : (
      <div className="center">加载中...</div>
    )
  }

  return (
    <div className="room-page">
      <div className="room-main">
        <div className="row-between">
          <div className="row">
            <h2>{room.name}</h2>
            {room.category && <span className="mini-chip">{room.category}</span>}
            {room.hasPassword && <span className="lock-badge">私密</span>}
          </div>
          <div className="row">
            {!isMember ? (
              <button className="btn" onClick={joinRoom} disabled={busy}>
                加入房间
              </button>
            ) : (
              <>
                <button className="btn" onClick={syncFocus} disabled={busy}>
                  同步专注
                </button>
                <button className="btn secondary" onClick={openInvite}>
                  邀请好友
                </button>
                <button className="btn secondary" onClick={leaveRoom}>
                  退出房间
                </button>
              </>
            )}
            {isOwner && (
              <>
                <button
                  className="btn secondary"
                  onClick={() => {
                    setEditForm({
                      name: room.name,
                      category: room.category || '',
                      announcement: room.announcement || '',
                      password: '',
                      focusMinutes: room.focusMinutes || '',
                      breakMinutes: room.breakMinutes || '',
                      aiTutorEnabled: room.aiTutorEnabled,
                      tutorPersona: room.tutorPersona || ''
                    })
                    setEditClearPassword(false)
                    setShowEdit(true)
                  }}
                >
                  编辑
                </button>
                <button
                  className="btn secondary"
                  onClick={() => {
                    setTransferTarget('')
                    setShowTransfer(true)
                  }}
                >
                  转移
                </button>
                <button className="btn danger ghost" onClick={deleteRoom}>
                  解散
                </button>
              </>
            )}
          </div>
        </div>
        {room.announcement && <div className="announcement">📌 {room.announcement}</div>}
        {error && <p className="error">{error}</p>}

        <div className="card timer-card">
          <h3>
            专注计时
            {room.focusMinutes > 0 && (
              <span className="mini-chip">房间默认 {room.focusMinutes} 分钟</span>
            )}
          </h3>
          {session ? (
            <>
              <div className="timer">
                {session.status === 'ACTIVE'
                  ? formatSeconds(elapsed)
                  : formatSeconds(session.durationSeconds || 0)}
              </div>
              <p className="muted">
                开始于 {formatTime(session.startedAt)}
                {session.endedAt ? ` · 结束于 ${formatTime(session.endedAt)}` : ''}
                {rounds > 0 && ` · 今日第 ${rounds} 轮`}
              </p>
              {session.status === 'ACTIVE' ? (
                <>
                  <button className="btn danger" onClick={stopFocus} disabled={busy}>
                    结束专注
                  </button>
                  <button className="btn secondary" onClick={() => setImmersive(true)}>
                    🎧 沉浸模式
                  </button>
                </>
              ) : (
                <>
                  {shortNotice && (
                    <div className="break-box warn">
                      <div className="break-label">⚠️ 本次专注不足 15 分钟，未计入统计</div>
                    </div>
                  )}
                  {breakActive && breakLeft !== null && (
                    <div className="break-box">
                      <div className="break-label">☕ 休息中</div>
                      <div className="break-timer">
                        {String(Math.floor(breakLeft / 60)).padStart(2, '0')}:
                        {String(breakLeft % 60).padStart(2, '0')}
                      </div>
                      <button className="btn tiny secondary" onClick={skipBreak}>
                        跳过休息
                      </button>
                    </div>
                  )}
                  {breakDone && (
                    <div className="break-box">
                      <div className="break-label">休息结束，能量满格！</div>
                    </div>
                  )}
                  <div className="row center-row">
                    <button className="btn" onClick={generateSummary} disabled={busy}>
                      AI 总结本次学习
                    </button>
                    <button className="btn" onClick={startFocus} disabled={busy}>
                      开始下一轮
                    </button>
                  </div>
                </>
              )}
              {summary && (
                <div className="summary">
                  <strong>AI 总结：</strong>
                  <div className="md">
                    <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                      {summary}
                    </ReactMarkdown>
                  </div>
                </div>
              )}
              {session.status === 'FINISHED' && (session.durationSeconds || 0) >= 900 && (
                <div className="recap-card">
                  <strong>💭 本次复盘</strong>
                  <textarea
                    placeholder="刚才的专注感觉如何？收获了什么？写一句心得，沉淀到个人主页..."
                    value={reflectionText}
                    onChange={(e) => {
                      setReflectionText(e.target.value)
                      setReflectionSaved(false)
                    }}
                    maxLength={2000}
                    rows={3}
                  />
                  <div className="row">
                    <button
                      className="btn tiny"
                      onClick={saveReflection}
                      disabled={busy || !reflectionText.trim() || reflectionSaved}
                    >
                      {reflectionSaved ? '✓ 已保存' : '保存心得'}
                    </button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="row center-row">
              <button className="btn" onClick={startFocus} disabled={busy || !isMember}>
                开始专注
              </button>
              {!isMember && <p className="muted">加入房间后才能开始专注</p>}
            </div>
          )}
        </div>

        <div className="card">
          <div className="row-between">
            <h3>房间聊天</h3>
            {room.aiTutorEnabled && (
              <div className="category-chips">
                <button
                  className={`chip-btn ${!tutorMode ? 'active' : ''}`}
                  onClick={() => setTutorMode(false)}
                >
                  成员
                </button>
                <button
                  className={`chip-btn ${tutorMode ? 'active' : ''}`}
                  onClick={() => setTutorMode(true)}
                >
                  🤖 助教
                </button>
              </div>
            )}
          </div>
          {mentionToast && (
            <div className="mention-toast" onClick={() => setMentionToast(null)}>
              <strong>{mentionToast.fromUsername}</strong> 在「{mentionToast.roomName || '房间'}
              」提到了你：
              {mentionToast.content}
              <button
                type="button"
                className="mention-toast-close"
                aria-label="关闭提醒"
                onClick={(e) => {
                  e.stopPropagation()
                  setMentionToast(null)
                }}
              >
                ✕
              </button>
            </div>
          )}
          <div className="chat-list">
            {hasMore && (
              <button
                className="btn tiny secondary load-more"
                onClick={loadOlder}
                disabled={loadingOlder}
              >
                {loadingOlder ? '加载中...' : '加载更早消息'}
              </button>
            )}
            {messages.length === 0 && <p className="muted">还没有消息，来打个招呼吧。</p>}
            {messages.map((msg) => (
              <div className="chat-item" key={msg.id}>
                <span className="chat-user">{msg.username}</span>
                <span className="chat-time">{formatTime(msg.createdAt)}</span>
                <div className="chat-content">{renderContent(msg.content)}</div>
              </div>
            ))}
          </div>
          {isMember && (
            <div className="chat-input-wrap">
              {mentionOpen && (
                <div className="mention-hint">
                  {mentionList.map((name) => (
                    <button
                      key={name}
                      className="mention-option"
                      type="button"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => pickMention(name)}
                    >
                      @{name}
                    </button>
                  ))}
                </div>
              )}
              <form className="inline-form" onSubmit={sendMessage}>
                <input
                  ref={inputRef}
                  placeholder={
                    tutorMode
                      ? '向 AI 助教提问...'
                      : '说点什么... 输入 @ 可以提醒成员'
                  }
                  value={input}
                  onChange={(e) => onInputChange(e.target.value)}
                  maxLength={1000}
                />
                <button disabled={!input.trim() || tutorBusy}>
                  {tutorBusy ? '思考中...' : '发送'}
                </button>
              </form>
            </div>
          )}
        </div>
      </div>

      <div className="room-side">
        <div className="card">
          <h3>在线成员 · {online}</h3>
          <ul className="member-list">
            {room.members.map((name) => (
              <li className="member-item" key={name}>
                <span>
                  <span className="member-avatar">{name.slice(0, 1).toUpperCase()}</span>
                  {name}
                  {name === room.ownerUsername && <span className="owner-badge">房主</span>}
                  {focusMap[name] && <span className="focus-badge">专注中</span>}
                </span>
                {isOwner && name !== room.ownerUsername && (
                  <button className="btn tiny danger ghost" onClick={() => kick(name)}>
                    移除
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
        <div className="card">
          <h3>🎧 氛围音</h3>
          <div className="row noise-row">
            <button
              className="btn tiny secondary"
              onClick={() => noise.start('brown')}
              disabled={noise.playing}
            >
              🌧️ 雨声
            </button>
            <button
              className="btn tiny secondary"
              onClick={() => noise.start('white')}
              disabled={noise.playing}
            >
              ❄️ 白噪
            </button>
            <button
              className="btn tiny secondary"
              onClick={() => noise.start('pink')}
              disabled={noise.playing}
            >
              🌊 粉噪
            </button>
            {noise.playing && (
              <button className="btn tiny danger ghost" onClick={noise.stop}>
                停止
              </button>
            )}
          </div>
          {noise.playing && (
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={noise.volume}
              onChange={(e) => noise.setVolume(Number(e.target.value))}
              className="volume-slider"
            />
          )}
        </div>
        <div className="card">
          <div className="row-between">
            <h3>学习排行榜</h3>
            <div className="category-chips">
              {[
                { key: 'today', label: '今日' },
                { key: 'week', label: '本周' },
                { key: 'month', label: '本月' },
                { key: 'all', label: '全部' }
              ].map((p) => (
                <button
                  key={p.key}
                  className={`chip-btn ${boardPeriod === p.key ? 'active' : ''}`}
                  onClick={() => setBoardPeriod(p.key)}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
          {leaderboard.length === 0 ? (
            <p className="muted">暂无学习记录</p>
          ) : (
            <ol>
              {leaderboard.map((entry) => (
                <li key={entry.username}>
                  {entry.username} · {entry.value} {entry.unit}
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>

      {showEdit && (
        <Modal title="编辑房间" onClose={() => setShowEdit(false)}>
          <form className="modal-form" onSubmit={saveEdit}>
            <label>
              房间名称 *
              <input
                value={editForm.name}
                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                maxLength={50}
              />
            </label>
            <label>
              标签
              <input
                value={editForm.category}
                onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                maxLength={30}
              />
            </label>
            <label>
              房间公告
              <textarea
                value={editForm.announcement}
                onChange={(e) => setEditForm({ ...editForm, announcement: e.target.value })}
                maxLength={1000}
                rows={3}
              />
            </label>
            <label>
              新加入密码（留空不修改）
              <input
                value={editForm.password}
                onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                maxLength={100}
              />
            </label>
            <label>
              单次专注时长（分钟，0 = 成员用自己的设置）
              <input
                type="number"
                min="0"
                max="180"
                value={editForm.focusMinutes}
                onChange={(e) => setEditForm({ ...editForm, focusMinutes: e.target.value })}
              />
            </label>
            <label>
              休息时长（分钟，0 = 成员用自己的设置）
              <input
                type="number"
                min="0"
                max="60"
                value={editForm.breakMinutes}
                onChange={(e) => setEditForm({ ...editForm, breakMinutes: e.target.value })}
              />
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={editForm.aiTutorEnabled}
                onChange={(e) => setEditForm({ ...editForm, aiTutorEnabled: e.target.checked })}
              />
              开启 AI 房间助教（成员可以在聊天里 @ 助教提问）
            </label>
            {editForm.aiTutorEnabled && (
              <label>
                助教人设（可选，如：耐心的高数助教，喜欢举例）
                <textarea
                  value={editForm.tutorPersona}
                  onChange={(e) => setEditForm({ ...editForm, tutorPersona: e.target.value })}
                  maxLength={200}
                  rows={2}
                />
              </label>
            )}
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={editClearPassword}
                onChange={(e) => setEditClearPassword(e.target.checked)}
              />
              设为公开房间（清除密码）
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setShowEdit(false)}>
                取消
              </button>
              <button disabled={busy || !editForm.name.trim()}>保存</button>
            </div>
          </form>
        </Modal>
      )}

      {showTransfer && (
        <Modal title="转移房主" onClose={() => setShowTransfer(false)}>
          <form className="modal-form" onSubmit={transfer}>
            <label>
              新房主
              <select value={transferTarget} onChange={(e) => setTransferTarget(e.target.value)}>
                <option value="">请选择房间成员</option>
                {room.members
                  .filter((name) => name !== room.ownerUsername)
                  .map((name) => (
                    <option key={name} value={name}>
                      {name}
                    </option>
                  ))}
              </select>
            </label>
            <div className="modal-actions">
              <button
                type="button"
                className="btn secondary"
                onClick={() => setShowTransfer(false)}
              >
                取消
              </button>
              <button disabled={busy || !transferTarget}>转移</button>
            </div>
          </form>
        </Modal>
      )}

      {showJoinPassword && (
        <Modal title={`加入「${room.name}」`} onClose={() => setShowJoinPassword(false)}>
          <form
            className="modal-form"
            onSubmit={(e) => {
              e.preventDefault()
              doJoin(joinPassword)
            }}
          >
            <p className="muted">该房间是私密的，需要密码才能加入。</p>
            <label>
              房间密码
              <input
                value={joinPassword}
                onChange={(e) => setJoinPassword(e.target.value)}
                maxLength={100}
                autoFocus
              />
            </label>
            <div className="modal-actions">
              <button
                type="button"
                className="btn secondary"
                onClick={() => setShowJoinPassword(false)}
              >
                取消
              </button>
              <button disabled={busy || !joinPassword}>加入</button>
            </div>
          </form>
        </Modal>
      )}

      {syncBanner && (
        <Modal title="同步专注" onClose={() => setSyncBanner(null)}>
          <div className="modal-form">
            <p>
              <strong>{syncBanner.username}</strong> 发起了同步专注，要一起开始吗？
            </p>
            <div className="modal-actions">
              <button className="btn secondary" onClick={() => setSyncBanner(null)}>
                忽略
              </button>
              <button
                onClick={() => {
                  setSyncBanner(null)
                  startFocus()
                }}
                disabled={busy}
              >
                加入专注
              </button>
            </div>
          </div>
        </Modal>
      )}

      {showInvite && (
        <Modal title="邀请好友" onClose={() => setShowInvite(false)}>
          <div className="modal-form">
            {inviteMsg && <p className="ok">{inviteMsg}</p>}
            {friends.length === 0 ? (
              <p className="muted">还没有好友，先去好友页添加吧。</p>
            ) : (
              friends.map((f) => (
                <div className="friend-row" key={f.username}>
                  <span className="friend-name">{f.username}</span>
                  <button className="btn tiny" onClick={() => sendInvite(f.username)}>
                    邀请
                  </button>
                </div>
              ))
            )}
            <div className="modal-actions">
              <button className="btn secondary" onClick={() => setShowInvite(false)}>
                关闭
              </button>
            </div>
          </div>
        </Modal>
      )}

      {immersive && session?.status === 'ACTIVE' && (
        <div className="immersive-overlay">
          <div className="immersive-label">深 度 专 注 中</div>
          <div className="immersive-room">{room.name}</div>
          <div className="immersive-timer">{formatSeconds(elapsed)}</div>
          <p className="muted">
            开始于 {formatTime(session.startedAt)}
            {roomFocusMinutes > 0 && ` · ${roomFocusMinutes} 分钟后自动结束`}
          </p>
          <div className="row">
            <button
              className="btn danger"
              onClick={() => {
                setImmersive(false)
                stopFocus()
              }}
              disabled={busy}
            >
              结束专注
            </button>
            <button className="btn secondary" onClick={() => setImmersive(false)}>
              退出沉浸
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
