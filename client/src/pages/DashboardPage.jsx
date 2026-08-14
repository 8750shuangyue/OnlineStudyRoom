import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useUnread } from '../useUnread.js'

function formatMinutes(seconds) {
  return Math.floor(seconds / 60)
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function formatRelative(iso) {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

export default function DashboardPage() {
  const { user } = useAuth()
  const { total: totalUnread } = useUnread()
  const [stats, setStats] = useState(null)
  const [goal, setGoal] = useState(null)
  const [achievements, setAchievements] = useState(null)
  const [rooms, setRooms] = useState([])
  const [invites, setInvites] = useState([])
  const [friendRequests, setFriendRequests] = useState([])
  const [activeSession, setActiveSession] = useState(null)
  const [trend, setTrend] = useState([])
  const [activities, setActivities] = useState([])
  const [tasks, setTasks] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [s, g, a, r, inv, fr, sess, t, feed, tk] = await Promise.all([
        api('/api/stats/me'),
        api('/api/goals'),
        api('/api/achievements'),
        api('/api/rooms/mine'),
        api('/api/invites'),
        api('/api/friends/requests'),
        api('/api/sessions/active'),
        api('/api/stats/trend?days=7'),
        api('/api/feed'),
        api('/api/tasks/daily')
      ])
      setStats(s)
      setGoal(g)
      setAchievements(a)
      setRooms(r)
      setInvites(inv)
      setFriendRequests(fr)
      setActiveSession(sess)
      setTrend(t)
      setActivities(feed.map((a) => ({ ...a, relative: formatRelative(a.createdAt) })))
      setTasks(tk)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  async function claimTask(key) {
    setError('')
    try {
      const claimed = await api('/api/tasks/daily/claim', { method: 'POST', body: { key } })
      setTasks((prev) => prev.map((t) => (t.key === key ? claimed : t)))
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => {
    load()
  }, [load])

  const todayMinutes = stats ? formatMinutes(stats.todayDurationSeconds) : 0
  const ringStyle = goal
    ? {
        background: `conic-gradient(#22d3ee ${goal.progressPercent * 3.6}deg, rgba(255,255,255,0.08) 0deg)`
      }
    : {}
  const earnedBadges = useMemo(
    () => (achievements ? achievements.badges.filter((b) => b.earned).length : 0),
    [achievements]
  )
  const xpInfo = achievements?.stats
  const xpPercent =
    xpInfo && xpInfo.xpNeededForNext > 0
      ? Math.min(100, Math.round((xpInfo.xpIntoLevel / xpInfo.xpNeededForNext) * 100))
      : 0
  const messageCount = totalUnread + invites.length + friendRequests.length
  const trendMax = useMemo(
    () => Math.max(1, ...trend.map((d) => formatMinutes(d.seconds))),
    [trend]
  )
  const activityIcons = {
    FOCUS_DONE: '⏱️',
    BADGE_EARNED: '🏅',
    FRIEND_ACCEPTED: '🤝',
    ROOM_CREATED: '🏠'
  }

  return (
    <div>
      <div className="dash-hero">
        <div>
          <h2>
            {greeting()}，{user?.username} 👋
          </h2>
          <p className="muted">
            {new Date().toLocaleDateString('zh-CN', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'long'
            })}
            ，今天也要保持专注哦。
          </p>
        </div>
        <div className="row">
          {activeSession?.roomId ? (
            <Link className="btn" to={`/rooms/${activeSession.roomId}`}>
              ▶ 继续专注
            </Link>
          ) : (
            <Link className="btn" to="/rooms">
              ▶ 开始专注
            </Link>
          )}
          <Link className="btn secondary" to="/rooms">
            ＋ 找房间
          </Link>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {!stats && !error && <p className="muted">加载中...</p>}

      {stats && (
        <div className="dash-grid">
          <div className="card dash-card dash-goal">
            <h3>🎯 今日目标</h3>
            <div className="ring" style={ringStyle}>
              <div className="ring-inner">
                <b>{todayMinutes}</b>
                <span>/ {goal ? goal.goalMinutes : 0} 分钟</span>
              </div>
            </div>
            <div className="row dash-mini-stats">
              <span>今日 {stats.todaySessions} 次</span>
              <span>累计 {formatMinutes(stats.totalDurationSeconds)} 分钟</span>
            </div>
          </div>

          <div className="card dash-card">
            <h3>📅 今日任务</h3>
            {tasks ? (
              <div className="task-list">
                {tasks.map((t) => (
                  <div className="task-item" key={t.key}>
                    <div className="task-info">
                      <b>{t.title}</b>
                      <span className="muted">{t.desc}</span>
                    </div>
                    {t.rewarded ? (
                      <span className="ok">已领取 +10 XP</span>
                    ) : t.done ? (
                      <button className="btn tiny" onClick={() => claimTask(t.key)}>
                        领取 +10 XP
                      </button>
                    ) : (
                      <span className="muted">
                        {t.progress}/{t.target}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="muted">加载中...</p>
            )}
            <div className="row" style={{ marginTop: 12 }}>
              <Link className="btn tiny secondary" to="/export/report">
                📄 学习报告
              </Link>
            </div>
          </div>

          <div className="card dash-card">
            <h3>🔥 连续打卡</h3>
            {xpInfo ? (
              <>
                <div className="dash-big-num">
                  {xpInfo.streak}
                  <span>天</span>
                </div>
                <div className="dash-level-row">
                  <span className="mini-chip">Lv.{xpInfo.level}</span>
                  <span className="muted">
                    {xpInfo.xpIntoLevel}/{xpInfo.xpNeededForNext} XP
                  </span>
                </div>
                <div className="xp-bar">
                  <div className="xp-bar-fill" style={{ width: `${xpPercent}%` }} />
                </div>
                <div className="row dash-mini-stats">
                  <span>
                    🏅 徽章 {earnedBadges}/{achievements.badges.length}
                  </span>
                  <span>最佳 {xpInfo.bestStreak} 天</span>
                </div>
              </>
            ) : (
              <p className="muted">暂无数据</p>
            )}
          </div>

          <div className="card dash-card">
            <h3>
              💬 待处理
              {messageCount > 0 && <span className="unread-badge">{messageCount}</span>}
            </h3>
            <div className="dash-todo">
              <Link to="/rooms">
                <span>房间未读消息</span>
                <b>{totalUnread}</b>
              </Link>
              <Link to="/friends">
                <span>好友请求</span>
                <b>{friendRequests.length}</b>
              </Link>
              <Link to="/rooms">
                <span>房间邀请</span>
                <b>{invites.length}</b>
              </Link>
            </div>
            <div className="row dash-actions">
              <Link className="btn tiny secondary" to="/friends">
                去处理
              </Link>
            </div>
          </div>

          <div className="card dash-card dash-trend">
            <h3>📈 近 7 天</h3>
            {trend.length === 0 ? (
              <p className="muted">还没有专注记录</p>
            ) : (
              <div className="mini-bars">
                {trend.map((d) => {
                  const minutes = formatMinutes(d.seconds)
                  return (
                    <div className="mini-bar-col" key={d.date} title={`${d.date}: ${minutes} 分钟`}>
                      <div
                        className="mini-bar"
                        style={{ height: `${Math.max(4, (minutes / trendMax) * 100)}%` }}
                      />
                      <span>{d.date.slice(5)}</span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {activities.length > 0 && (
        <div className="card">
          <h3>🫧 好友动态</h3>
          <div className="msg-list">
            {activities.slice(0, 8).map((act) => (
              <div className="msg-item" key={act.id}>
                <div className="msg-title">
                  <span>{activityIcons[act.type] || '✨'}</span>
                  <span>
                    <strong>{act.username}</strong> {act.text}
                  </span>
                  <span className="msg-time">{act.relative}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card">
        <div className="row-between">
          <h3>🏠 我的房间</h3>
          <Link className="btn tiny secondary" to="/rooms">
            全部房间
          </Link>
        </div>
        {rooms.length === 0 ? (
          <p className="muted">
            还没有加入任何房间，<Link to="/rooms">去创建或加入</Link>一个吧。
          </p>
        ) : (
          <div className="dash-room-list">
            {rooms.slice(0, 6).map((room) => (
              <Link className="dash-room" to={`/rooms/${room.id}`} key={room.id}>
                <span className="dash-room-name">
                  {room.name}
                  {room.hasPassword && <span className="lock-badge">私密</span>}
                </span>
                <span className="muted">
                  {room.category ? `${room.category} · ` : ''}
                  {room.memberCount} 人
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
