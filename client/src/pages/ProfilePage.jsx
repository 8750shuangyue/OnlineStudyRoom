import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api } from '../api.js'

const BADGE_ICONS = {
  FIRST_FOCUS: '🐣',
  SESSIONS_10: '🚀',
  SESSIONS_50: '💪',
  MINUTES_100: '⚒️',
  MINUTES_500: '🛣️',
  MINUTES_1000: '🌏',
  STREAK_3: '✨',
  STREAK_7: '📅',
  STREAK_30: '🦾',
  DAYS_7: '📆',
  MARATHON: '🏃',
  NIGHT_OWL: '🦉'
}

function formatDuration(seconds) {
  if (!seconds) return '0 分钟'
  return `${Math.round(seconds / 60)} 分钟`
}

export default function ProfilePage() {
  const { username } = useParams()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setProfile(await api(`/api/users/${encodeURIComponent(username)}`))
    } catch (err) {
      setError(err.message)
    }
  }, [username])

  useEffect(() => {
    load()
  }, [load])

  if (error) {
    return (
      <div className="center">
        <p className="error">{error}</p>
      </div>
    )
  }
  if (!profile) {
    return <p className="muted">加载中...</p>
  }

  const stats = profile.stats
  const earnedBadges = profile.badges.filter((b) => b.earned)
  const xpPercent =
    stats.xpNeededForNext > 0
      ? Math.min(100, Math.round((stats.xpIntoLevel / stats.xpNeededForNext) * 100))
      : 0

  return (
    <div>
      <div className="card profile-head">
        <div className="profile-avatar">{username.slice(0, 1).toUpperCase()}</div>
        <div className="profile-head-info">
          <h2>{profile.username}</h2>
          <div className="row">
            <span className="mini-chip">Lv.{stats.level}</span>
            <span className="mini-chip">🔥 连续 {stats.streak} 天</span>
            <span className="mini-chip">🏅 {earnedBadges.length} 枚徽章</span>
          </div>
          <div className="xp-bar profile-xp">
            <div className="xp-bar-fill" style={{ width: `${xpPercent}%` }} />
          </div>
          <span className="muted">
            {stats.xpIntoLevel}/{stats.xpNeededForNext} XP
          </span>
        </div>
        <div className="profile-stats">
          <div>
            <b>{stats.totalMinutes}</b>
            <span>累计分钟</span>
          </div>
          <div>
            <b>{stats.totalSessions}</b>
            <span>专注次数</span>
          </div>
          <div>
            <b>{stats.bestStreak}</b>
            <span>最佳连续</span>
          </div>
          <div>
            <b>{stats.distinctDays}</b>
            <span>活跃天数</span>
          </div>
        </div>
      </div>

      <div className="card">
        <h3>🏅 徽章墙 · {earnedBadges.length}/{profile.badges.length}</h3>
        <div className="badge-grid">
          {profile.badges.map((badge) => (
            <div className={`badge-item ${badge.earned ? 'earned' : 'locked'}`} key={badge.code}>
              <div className="badge-emoji">{BADGE_ICONS[badge.code] || '🏅'}</div>
              <div className="badge-name">{badge.name}</div>
              <div className="badge-desc">{badge.description}</div>
              {badge.earned ? (
                <div className="badge-earned">✓ 已获得</div>
              ) : (
                <div className="muted">未解锁</div>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>🎖️ 称号</h3>
        <div className="title-list">
          {profile.titles.map((t) => (
            <span className={`title-chip ${t.unlocked ? 'unlocked' : ''}`} key={t.name}>
              {t.name}
            </span>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>📚 最近专注</h3>
        {profile.recentSessions.length === 0 ? (
          <p className="muted">还没有专注记录</p>
        ) : (
          <div className="msg-list">
            {profile.recentSessions.map((s) => (
              <div className="msg-item" key={s.id}>
                <div className="msg-title">
                  {s.roomName}
                  <span className="msg-time">
                    {new Date(s.startedAt).toLocaleDateString('zh-CN')} · {formatDuration(s.durationSeconds)}
                  </span>
                </div>
                {s.reflection && <div className="msg-body">💭 {s.reflection}</div>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
