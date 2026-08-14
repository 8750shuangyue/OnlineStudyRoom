import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, copyText } from '../api.js'
import { BADGE_ICONS, SEASON_ICONS } from '../badges.js'

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function downloadCard(card) {
  const w = 640
  const h = 880
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  const grad = ctx.createLinearGradient(0, 0, w, h)
  grad.addColorStop(0, '#0f172a')
  grad.addColorStop(0.45, '#312e81')
  grad.addColorStop(1, '#155e75')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = 'rgba(255,255,255,0.07)'
  ctx.beginPath()
  ctx.arc(540, 90, 170, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.arc(70, 830, 150, 0, Math.PI * 2)
  ctx.fill()

  ctx.textAlign = 'center'
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 30px "Microsoft YaHei", sans-serif'
  ctx.fillText('🧑‍🎓 自习室名片', w / 2, 92)

  ctx.font = 'bold 84px sans-serif'
  ctx.fillText((card.username || '?').slice(0, 1).toUpperCase(), w / 2, 218)
  ctx.font = 'bold 38px "Microsoft YaHei", sans-serif'
  ctx.fillText(card.username, w / 2, 286)
  ctx.fillStyle = '#94a3b8'
  ctx.font = '20px "Microsoft YaHei", sans-serif'
  const joined = card.createdAt ? new Date(card.createdAt).toLocaleDateString('zh-CN') : ''
  ctx.fillText(`加入于 ${joined}`, w / 2, 326)

  const s = card.stats
  const cardY = 372
  ctx.fillStyle = 'rgba(255,255,255,0.09)'
  roundRect(ctx, 44, cardY, w - 88, 196, 26)
  ctx.fill()
  ctx.fillStyle = '#67e8f9'
  ctx.font = 'bold 60px sans-serif'
  ctx.fillText(`Lv.${s.level}`, w / 2, cardY + 78)
  ctx.fillStyle = '#94a3b8'
  ctx.font = '20px "Microsoft YaHei", sans-serif'
  ctx.fillText(`${s.xp} XP`, w / 2, cardY + 114)
  ctx.fillStyle = '#fbbf24'
  ctx.font = 'bold 30px sans-serif'
  ctx.fillText(`🔥 连续 ${s.streak} 天`, w / 2, cardY + 168)

  const stats = [
    [`${s.totalMinutes}`, '累计分钟'],
    [`${s.totalSessions}`, '专注次数'],
    [`${s.bestStreak}`, '最长连续'],
    [`${s.distinctDays}`, '活跃天数']
  ]
  stats.forEach(([value, label], i) => {
    const x = w / 2 - 200 + i * 104
    ctx.fillStyle = '#e2e8f0'
    ctx.font = 'bold 26px sans-serif'
    ctx.fillText(value, x, cardY + 196)
    ctx.fillStyle = '#94a3b8'
    ctx.font = '16px "Microsoft YaHei", sans-serif'
    ctx.fillText(label, x, cardY + 224)
  })

  const earned = card.badges.filter((b) => b.earned)
  ctx.fillStyle = '#fff'
  ctx.font = '22px "Microsoft YaHei", sans-serif'
  ctx.fillText(`🏅 徽章 ${earned.length}/${card.badges.length}`, w / 2, 640)
  const emojis = earned.slice(0, 10).map((b) => BADGE_ICONS[b.code] || '🌟')
  ctx.font = '44px sans-serif'
  const startX = w / 2 - (emojis.length - 1) * 30
  emojis.forEach((e, i) => ctx.fillText(e, startX + i * 60, 700))

  const seasonCount = card.seasonAwards.length
  ctx.fillStyle = '#fcd34d'
  ctx.font = '20px "Microsoft YaHei", sans-serif'
  ctx.fillText(`🏆 赛季徽章 ${seasonCount} 枚`, w / 2, 760)

  ctx.fillStyle = '#94a3b8'
  ctx.font = '18px "Microsoft YaHei", sans-serif'
  ctx.fillText('—— 网页版自习室 ——', w / 2, h - 40)

  const url = canvas.toDataURL('image/png')
  const a = document.createElement('a')
  a.href = url
  a.download = `${card.username}-card.png`
  a.click()
}

export default function PublicCardPage() {
  const { username } = useParams()
  const [card, setCard] = useState(null)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const load = useCallback(async () => {
    try {
      setCard(await api(`/api/users/${encodeURIComponent(username)}/card`, { auth: false }))
    } catch (err) {
      setError(err.message)
    }
  }, [username])

  useEffect(() => {
    load()
  }, [load])

  async function copyLink() {
    const url = window.location.href
    try {
      await copyText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // 剪贴板不可用时忽略
    }
  }

  if (error) {
    return (
      <div className="public-card-page">
        <div className="public-card error-card">
          <p className="error">加载失败：{error}</p>
          <Link className="btn" to="/">
            返回自习室
          </Link>
        </div>
      </div>
    )
  }
  if (!card) {
    return <div className="public-card-page center muted">加载中...</div>
  }

  const s = card.stats
  const xpPercent = s.xpNeededForNext > 0 ? Math.min(100, Math.round((s.xpIntoLevel / s.xpNeededForNext) * 100)) : 0
  const earned = card.badges.filter((b) => b.earned)
  const unlockedTitles = card.titles.filter((t) => t.unlocked)

  return (
    <div className="public-card-page">
      <div className="public-card">
        <div className="pc-glow pc-glow-1" />
        <div className="pc-glow pc-glow-2" />
        <div className="pc-head">
          <div className="pc-avatar">{card.username.slice(0, 1).toUpperCase()}</div>
          <h1>{card.username}</h1>
          <p className="muted">
            加入于 {new Date(card.createdAt).toLocaleDateString('zh-CN')}
          </p>
        </div>

        <div className="pc-level">
          <div className="level-ring" style={{ background: `conic-gradient(#22d3ee ${xpPercent * 3.6}deg, rgba(255,255,255,0.1) 0deg)` }}>
            <div className="level-ring-inner">
              <div className="level-num">Lv.{s.level}</div>
              <div className="muted">等级</div>
            </div>
          </div>
          <div className="pc-level-right">
            <div className="pc-xp-label">
              {s.xpIntoLevel}/{s.xpNeededForNext} XP · 连续专注 {s.streak} 天 🔥
            </div>
            <div className="xp-bar">
              <div className="xp-fill" style={{ width: `${xpPercent}%` }} />
            </div>
          </div>
        </div>

        <div className="profile-stats pc-stats">
          <div>
            <b>{s.totalMinutes}</b>
            <span>累计分钟</span>
          </div>
          <div>
            <b>{s.totalSessions}</b>
            <span>专注次数</span>
          </div>
          <div>
            <b>{s.bestStreak}</b>
            <span>最长连续</span>
          </div>
          <div>
            <b>{s.distinctDays}</b>
            <span>活跃天数</span>
          </div>
        </div>

        {unlockedTitles.length > 0 && (
          <div className="pc-section">
            <h3>🎖️ 称号</h3>
            <div className="title-list">
              {unlockedTitles.map((t) => (
                <span className="title-chip unlocked" key={t.name}>
                  {t.name}
                </span>
              ))}
            </div>
          </div>
        )}

        <div className="pc-section">
          <h3>
            🏅 徽章 {earned.length}/{card.badges.length}
          </h3>
          {earned.length === 0 ? (
            <p className="muted">还在攒第一枚徽章…</p>
          ) : (
            <div className="pc-badges">
              {earned.map((b) => (
                <span className="pc-badge" title={b.name} key={b.code}>
                  {BADGE_ICONS[b.code] || '🌟'}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="pc-section">
          <h3>🏆 赛季徽章 {card.seasonAwards.length} 枚</h3>
          {card.seasonAwards.length === 0 ? (
            <p className="muted">暂无赛季徽章</p>
          ) : (
            <div className="pc-badges">
              {card.seasonAwards.slice(0, 12).map((a) => (
                <span
                  className="pc-badge"
                  title={`${a.name}（${a.seasonKey}${a.extra ? ' · ' + a.extra : ''}）`}
                  key={`${a.code}-${a.seasonKey}`}
                >
                  {SEASON_ICONS[a.code] || '🏅'}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="pc-actions">
          <button className="btn" onClick={() => downloadCard(card)}>
            ⬇️ 下载名片
          </button>
          <button className="btn secondary" onClick={copyLink}>
            {copied ? '✅ 已复制' : '🔗 复制链接'}
          </button>
          <Link className="btn secondary" to="/">
            返回自习室
          </Link>
        </div>
      </div>
    </div>
  )
}
