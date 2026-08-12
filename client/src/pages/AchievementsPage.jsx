import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

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

const BADGE_COLORS = {
  FIRST_FOCUS: { gradient: 'linear-gradient(135deg,#fbbf24,#f59e0b)', glow: '#f59e0b' },
  SESSIONS_10: { gradient: 'linear-gradient(135deg,#60a5fa,#3b82f6)', glow: '#3b82f6' },
  SESSIONS_50: { gradient: 'linear-gradient(135deg,#f87171,#ef4444)', glow: '#ef4444' },
  MINUTES_100: { gradient: 'linear-gradient(135deg,#a78bfa,#8b5cf6)', glow: '#8b5cf6' },
  MINUTES_500: { gradient: 'linear-gradient(135deg,#34d399,#10b981)', glow: '#10b981' },
  MINUTES_1000: { gradient: 'linear-gradient(135deg,#22d3ee,#06b6d4)', glow: '#06b6d4' },
  STREAK_3: { gradient: 'linear-gradient(135deg,#fcd34d,#fbbf24)', glow: '#fbbf24' },
  STREAK_7: { gradient: 'linear-gradient(135deg,#c084fc,#a855f7)', glow: '#a855f7' },
  STREAK_30: { gradient: 'linear-gradient(135deg,#94a3b8,#64748b)', glow: '#94a3b8' },
  DAYS_7: { gradient: 'linear-gradient(135deg,#fb7185,#f43f5e)', glow: '#f43f5e' },
  MARATHON: { gradient: 'linear-gradient(135deg,#4ade80,#22c55e)', glow: '#22c55e' },
  NIGHT_OWL: { gradient: 'linear-gradient(135deg,#818cf8,#6366f1)', glow: '#6366f1' }
}

const CONFETTI_COLORS = ['#fbbf24', '#f472b6', '#22d3ee', '#a78bfa', '#34d399', '#60a5fa']

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function downloadShareCard(stats, username, badges) {
  const w = 640
  const h = 900
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  const grad = ctx.createLinearGradient(0, 0, w, h)
  grad.addColorStop(0, '#1e1b4b')
  grad.addColorStop(0.5, '#4c1d95')
  grad.addColorStop(1, '#155e75')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = 'rgba(255,255,255,0.06)'
  ctx.beginPath()
  ctx.arc(560, 120, 160, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.arc(60, 820, 140, 0, Math.PI * 2)
  ctx.fill()

  ctx.textAlign = 'center'
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 42px "Microsoft YaHei", sans-serif'
  ctx.fillText('✦ 我的自习室成就 ✦', w / 2, 110)
  ctx.font = '26px "Microsoft YaHei", sans-serif'
  ctx.fillStyle = '#c4b5fd'
  ctx.fillText(username || '自习室用户', w / 2, 158)

  const cardY = 210
  const cardH = 300
  ctx.fillStyle = 'rgba(255,255,255,0.08)'
  roundRect(ctx, 40, cardY, w - 80, cardH, 24)
  ctx.fill()
  ctx.fillStyle = '#67e8f9'
  ctx.font = 'bold 64px sans-serif'
  ctx.fillText(`Lv.${stats.level}`, w / 2, cardY + 92)
  ctx.fillStyle = '#94a3b8'
  ctx.font = '20px "Microsoft YaHei", sans-serif'
  ctx.fillText(`${stats.xp} XP`, w / 2, cardY + 132)
  ctx.fillStyle = '#fbbf24'
  ctx.font = 'bold 34px sans-serif'
  ctx.fillText(`🔥 连续 ${stats.streak} 天`, w / 2, cardY + 202)
  ctx.fillStyle = '#e6e9f2'
  ctx.font = '22px "Microsoft YaHei", sans-serif'
  ctx.fillText(`累计 ${stats.totalMinutes} 分钟 · ${stats.totalSessions} 次`, w / 2, cardY + 252)

  const earned = badges.filter((b) => b.earned)
  ctx.fillStyle = '#fff'
  ctx.font = '20px "Microsoft YaHei", sans-serif'
  ctx.fillText(`🏅 徽章 ${earned.length}/${badges.length}`, w / 2, cardY + 300)
  const emojis = earned.slice(0, 8).map((b) => BADGE_ICONS[b.code] || '🏅')
  ctx.font = '42px sans-serif'
  const startX = w / 2 - (emojis.length - 1) * 28
  emojis.forEach((e, i) => ctx.fillText(e, startX + i * 56, cardY + cardH + 48))

  ctx.fillStyle = '#94a3b8'
  ctx.font = '18px "Microsoft YaHei", sans-serif'
  ctx.fillText('— 来自网页版自习室 —', w / 2, h - 48)

  const url = canvas.toDataURL('image/png')
  const a = document.createElement('a')
  a.href = url
  a.download = 'achievement-card.png'
  a.click()
}

export default function AchievementsPage() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [simple, setSimple] = useState(() => localStorage.getItem('ach_simple') === '1')

  function toggleSimple() {
    setSimple((prev) => {
      const next = !prev
      localStorage.setItem('ach_simple', next ? '1' : '0')
      return next
    })
  }

  const confetti = useMemo(
    () =>
      Array.from({ length: 36 }, (_, i) => ({
        left: `${(i * 37) % 100}%`,
        delay: `${(i * 0.7) % 6}s`,
        duration: `${5 + (i % 5)}s`,
        color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
        width: 6 + (i % 3) * 4,
        height: 4 + (i % 2) * 6,
        sway: `${-90 + (i % 6) * 30}px`
      })),
    []
  )

  const particles = useMemo(
    () =>
      Array.from({ length: 18 }, (_, i) => ({
        left: `${(i * 53) % 100}%`,
        delay: `${(i * 1.3) % 8}s`,
        duration: `${9 + (i % 6)}s`,
        size: 2 + (i % 3),
        opacity: 0.35 + ((i % 4) * 0.15).toFixed(2)
      })),
    []
  )

  useEffect(() => {
    if (simple) {
      return undefined
    }
    let last = 0
    function onMove(e) {
      const now = Date.now()
      if (now - last < 60) {
        return
      }
      last = now
      const sparkle = document.createElement('span')
      sparkle.className = 'trail-sparkle'
      sparkle.textContent = Math.random() > 0.5 ? '✦' : '✧'
      sparkle.style.left = `${e.clientX - 8}px`
      sparkle.style.top = `${e.clientY - 8}px`
      document.body.appendChild(sparkle)
      setTimeout(() => sparkle.remove(), 900)
    }
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [simple])

  useEffect(() => {
    api('/api/achievements')
      .then(setData)
      .catch((err) => setError(err.message))
  }, [])

  if (error) {
    return <p className="error">{error}</p>
  }
  if (!data) {
    return <p className="muted">加载中...</p>
  }

  const s = data.stats
  const xpPercent = Math.min(100, Math.round((s.xpIntoLevel / s.xpNeededForNext) * 100))
  const xpToNext = s.xpNeededForNext - s.xpIntoLevel
  const ringStyle = {
    background: `conic-gradient(#22d3ee ${xpPercent * 3.6}deg, rgba(255,255,255,0.08) 0deg)`
  }

  return (
    <div className={`achievements-page ${simple ? 'simple' : ''}`}>
      {!simple && (
        <>
          <div className="confetti-layer">
            {confetti.map((c, i) => (
              <span
                key={i}
                className="confetti"
                style={{
                  left: c.left,
                  background: c.color,
                  width: c.width,
                  height: c.height,
                  animationDelay: c.delay,
                  animationDuration: c.duration,
                  '--sway': c.sway
                }}
              />
            ))}
          </div>
          <div className="particle-layer">
            {particles.map((p, i) => (
              <span
                key={i}
                className="particle"
                style={{
                  left: p.left,
                  width: p.size,
                  height: p.size,
                  animationDelay: p.delay,
                  animationDuration: p.duration,
                  '--op': p.opacity
                }}
              />
            ))}
          </div>
          <span className="firework fw-1" />
          <span className="firework fw-2" />
          <span className="firework fw-3" />
          <div className="spotlight sp-l" />
          <div className="spotlight sp-r" />
          <div className="float-emoji fe-1">📚</div>
          <div className="float-emoji fe-2">⏰</div>
          <div className="float-emoji fe-3">🎯</div>
          <div className="float-emoji fe-4">🦉</div>
        </>
      )}

      <div className="row-between">
        <h2 className="fancy-title">我的成就</h2>
        <div className="row">
          <span className="muted">坚持就有回报 ✨</span>
          <button
            className="chip-btn"
            onClick={() => data?.stats && downloadShareCard(data.stats, user?.username, data.badges)}
          >
            🖼 分享成就卡
          </button>
          <button
            className={`chip-btn mode-toggle ${simple ? 'active' : ''}`}
            onClick={toggleSimple}
          >
            {simple ? '🧘 简洁模式' : '✨ 华丽模式'}
          </button>
        </div>
      </div>

      <div className="glow-frame">
        <div className="card level-card">
          <div className="level-ring" style={ringStyle}>
            <div className="level-ring-inner">
              <div className="level-num">Lv.{s.level}</div>
              <div className="muted">等级</div>
            </div>
          </div>
          <div className="level-info">
            <div className="level-title">
              专注成长之路
              <span className="streak-flame">🔥 连续 {s.streak} 天</span>
            </div>
            <div className="xp-bar">
              <div className="xp-fill" style={{ width: `${xpPercent}%` }} />
            </div>
            <div className="muted">
              经验 {s.xp} · 距离 Lv.{s.level + 1} 还差 {xpToNext} XP
            </div>
            <div className="level-stats">
              <span>📖 {s.totalSessions} 次专注</span>
              <span>⏱ {s.totalMinutes} 分钟</span>
              <span>📆 {s.distinctDays} 天打卡</span>
              <span>🏆 最高 {s.bestStreak} 天</span>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <h3>🏅 称号</h3>
        <div className="title-list">
          {data.titles.map((t) => (
            <span
              key={t.name}
              className={`title-chip ${t.unlocked ? 'unlocked' : 'locked'}`}
              title={t.description}
            >
              {t.unlocked ? '✓' : '🔒'} {t.name}
            </span>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>🗺️ 徽章图鉴</h3>
        <div className="badge-grid">
          {data.badges.map((b) => {
            const color = BADGE_COLORS[b.code]
            const style =
              b.earned && color
                ? { background: color.gradient, '--glow': `${color.glow}66` }
                : undefined
            return (
              <div
                key={b.code}
                className={`badge-item ${b.earned ? 'earned' : 'locked'}`}
                style={style}
              >
                <div className="badge-emoji">{BADGE_ICONS[b.code] || '🏅'}</div>
                {b.earned && (
                  <>
                    <span className="sparkle sp-1">✦</span>
                    <span className="sparkle sp-2">✦</span>
                    <span className="ring-burst" />
                  </>
                )}
                <div className="badge-name">{b.name}</div>
                <div className="badge-desc">{b.description}</div>
                {b.earned && <div className="badge-earned">✓ 已获得</div>}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
