import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { getToken } from '../api.js'

function formatMinutes(seconds) {
  return Math.floor(seconds / 60)
}

const PERIODS = [
  { key: 'today', label: '今日' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'all', label: '全部' }
]

const METRICS = [
  { key: 'duration', label: '时长' },
  { key: 'sessions', label: '次数' },
  { key: 'streak', label: '连续打卡' },
  { key: 'bestTime', label: '最佳时段' }
]

const HEAT_COLORS = [
  'rgba(255,255,255,0.06)',
  'rgba(34,211,238,0.25)',
  'rgba(34,211,238,0.5)',
  'rgba(34,211,238,0.75)',
  'rgba(34,211,238,1)'
]

function heatColor(minutes) {
  if (minutes <= 0) return HEAT_COLORS[0]
  if (minutes < 30) return HEAT_COLORS[1]
  if (minutes < 60) return HEAT_COLORS[2]
  if (minutes < 120) return HEAT_COLORS[3]
  return HEAT_COLORS[4]
}

export default function StatsPage() {
  const [stats, setStats] = useState(null)
  const [goal, setGoal] = useState(null)
  const [goalInput, setGoalInput] = useState('')
  const [period, setPeriod] = useState('all')
  const [metric, setMetric] = useState('duration')
  const [board, setBoard] = useState([])
  const [weekly, setWeekly] = useState(null)
  const [trend, setTrend] = useState([])
  const [heatmap, setHeatmap] = useState([])
  const [timeBuckets, setTimeBuckets] = useState([])
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [s, g, b, w, t, h, ta] = await Promise.all([
        api('/api/stats/me'),
        api('/api/goals'),
        api(`/api/leaderboard/global?period=${period}&metric=${metric}`),
        api('/api/stats/weekly'),
        api('/api/stats/trend?days=14'),
        api('/api/stats/heatmap'),
        api('/api/stats/time-analysis')
      ])
      setStats(s)
      setGoal(g)
      setBoard(b)
      setWeekly(w)
      setTrend(t)
      setHeatmap(h)
      setTimeBuckets(ta)
    } catch (err) {
      setError(err.message)
    }
  }, [period, metric])

  useEffect(() => {
    load()
  }, [load])

  async function saveGoal(e) {
    e.preventDefault()
    setError('')
    try {
      const g = await api('/api/goals', { method: 'PUT', body: { goalMinutes: Number(goalInput) } })
      setGoal(g)
      setGoalInput('')
    } catch (err) {
      setError(err.message)
    }
  }

  async function exportCsv() {
    setError('')
    try {
      const resp = await fetch('/api/export/sessions.csv', {
        headers: { Authorization: `Bearer ${getToken()}` }
      })
      if (!resp.ok) {
        throw new Error(`导出失败（HTTP ${resp.status}）`)
      }
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'study-sessions.csv'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err.message)
    }
  }

  const trendMax = useMemo(
    () => Math.max(1, ...trend.map((d) => formatMinutes(d.seconds))),
    [trend]
  )

  const trendLine = useMemo(() => {
    if (trend.length === 0) return ''
    const w = 600
    const h = 130
    const pad = 12
    const step = (w - pad * 2) / Math.max(1, trend.length - 1)
    return trend
      .map(
        (d, i) =>
          `${(pad + i * step).toFixed(1)},${(h - pad - (formatMinutes(d.seconds) / trendMax) * (h - pad * 2)).toFixed(1)}`
      )
      .join(' ')
  }, [trend, trendMax])

  const trendArea = useMemo(() => {
    if (!trendLine) return ''
    return `0,130 ${trendLine} 600,130`
  }, [trendLine])

  const heatCells = useMemo(() => {
    const days = []
    const today = new Date()
    for (let i = 89; i >= 0; i--) {
      const d = new Date(today)
      d.setDate(d.getDate() - i)
      days.push(d)
    }
    const cells = []
    for (let w = 0; w < 13; w++) {
      for (let r = 0; r < 7; r++) {
        const idx = w * 7 + r
        if (idx < days.length) cells.push(days[idx])
      }
    }
    return cells
  }, [])

  const heatByDate = useMemo(
    () => Object.fromEntries(heatmap.map((d) => [d.date, formatMinutes(d.seconds)])),
    [heatmap]
  )

  const maxTime = useMemo(() => Math.max(1, ...timeBuckets.map((b) => b.minutes)), [timeBuckets])

  const ringStyle = goal
    ? {
        background: `conic-gradient(#22d3ee ${goal.progressPercent * 3.6}deg, rgba(255,255,255,0.08) 0deg)`
      }
    : {}

  return (
    <div>
      <div className="row-between">
        <h2>我的学习看板</h2>
        <button className="btn secondary" onClick={exportCsv}>
          ⬇ 导出 CSV
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {!stats && !error && <p className="muted">加载中...</p>}
      {stats && (
        <>
          <div className="stats-grid">
            <div className="card stat-card">
              <div className="stat-num">{stats.totalSessions}</div>
              <div className="stat-label">累计专注次数</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{formatMinutes(stats.totalDurationSeconds)}</div>
              <div className="stat-label">累计专注分钟</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{stats.todaySessions}</div>
              <div className="stat-label">今日次数</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{formatMinutes(stats.todayDurationSeconds)}</div>
              <div className="stat-label">今日分钟</div>
            </div>
          </div>

          {weekly && (
            <div className="card weekly-card">
              <h3>📊 本周报告</h3>
              <div className="weekly-stats">
                <div>
                  <b>{weekly.totalMinutes}</b>
                  <span>专注分钟</span>
                </div>
                <div>
                  <b>{weekly.totalSessions}</b>
                  <span>专注次数</span>
                </div>
                <div>
                  <b>{weekly.daysActive}</b>
                  <span>活跃天数</span>
                </div>
                {weekly.bestDay && (
                  <div>
                    <b>{weekly.bestDayMinutes}</b>
                    <span>最佳日 {weekly.bestDay}</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {trend.length > 0 && (
            <div className="card">
              <h3>📈 近 14 天专注趋势</h3>
              <svg viewBox="0 0 600 140" className="trend-chart" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#22d3ee" stopOpacity="0.5" />
                    <stop offset="100%" stopColor="#22d3ee" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <polygon points={trendArea} fill="url(#trendFill)" />
                <polyline points={trendLine} fill="none" stroke="#22d3ee" strokeWidth="2.5" />
                {trend.map((d, i) => {
                  const minutes = formatMinutes(d.seconds)
                  if (minutes <= 0) return null
                  const x = (12 + (i * (600 - 24)) / Math.max(1, trend.length - 1)).toFixed(1)
                  const y = (130 - 12 - (minutes / trendMax) * (130 - 24)).toFixed(1)
                  return <circle key={d.date} cx={x} cy={y} r="3.5" fill="#67e8f9" />
                })}
              </svg>
              <div className="trend-labels">
                {trend.map((d) => (
                  <span key={d.date} title={`${d.date}: ${formatMinutes(d.seconds)} 分钟`}>
                    {d.date.slice(5)}
                  </span>
                ))}
              </div>
            </div>
          )}

          {heatCells.length > 0 && (
            <div className="card">
              <h3>🗓️ 近 90 天打卡热力图</h3>
              <div className="heat-grid">
                {heatCells.map((d) => {
                  const key = d.toISOString().slice(0, 10)
                  const minutes = heatByDate[key] || 0
                  return (
                    <span
                      key={key}
                      className="heat-cell"
                      style={{ background: heatColor(minutes) }}
                      title={`${key}：${minutes} 分钟`}
                    />
                  )
                })}
              </div>
              <div className="heat-legend">
                <span>少</span>
                {HEAT_COLORS.map((c) => (
                  <span key={c} className="heat-cell" style={{ background: c }} />
                ))}
                <span>多</span>
              </div>
            </div>
          )}

          {timeBuckets.length > 0 && (
            <div className="card">
              <h3>🕐 时段效率分析（近 90 天）</h3>
              <div className="time-bars">
                {timeBuckets.map((b) => (
                  <div className="time-bar-row" key={b.label}>
                    <span className="time-label">{b.label}</span>
                    <div className="time-track">
                      <div
                        className="time-fill"
                        style={{ width: `${(b.minutes / maxTime) * 100}%` }}
                      />
                    </div>
                    <span className="time-value">{b.minutes} 分钟</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {goal && (
            <div className="card goal-card">
              <div className="goal-ring" style={ringStyle}>
                <div className="goal-ring-inner">
                  <div className="goal-num">{goal.progressPercent}%</div>
                  <div className="muted">今日目标</div>
                </div>
              </div>
              <div className="goal-info">
                <h3>每日目标</h3>
                <p className="muted">
                  今日已专注 {goal.todayMinutes} / {goal.goalMinutes} 分钟
                </p>
                <form className="inline-form" onSubmit={saveGoal}>
                  <input
                    type="number"
                    min={10}
                    max={1440}
                    value={goalInput}
                    placeholder={`当前目标 ${goal.goalMinutes} 分钟`}
                    onChange={(e) => setGoalInput(e.target.value)}
                  />
                  <button disabled={!goalInput}>设置目标</button>
                </form>
              </div>
            </div>
          )}

          <div className="card">
            <div className="row-between">
              <h3>🏆 全站排行榜</h3>
              <div className="board-controls">
                <div className="category-chips">
                  {METRICS.map((m) => (
                    <button
                      key={m.key}
                      className={`chip-btn ${metric === m.key ? 'active' : ''}`}
                      onClick={() => setMetric(m.key)}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
                <div className="category-chips">
                  {PERIODS.map((p) => (
                    <button
                      key={p.key}
                      className={`chip-btn ${period === p.key ? 'active' : ''}`}
                      onClick={() => setPeriod(p.key)}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            {board.length === 0 ? (
              <p className="muted">该时段暂无学习数据</p>
            ) : (
              <ol className="board-list">
                {board.map((entry, i) => (
                  <li key={entry.username} className={i === 0 ? 'board-top' : ''}>
                    <span className="board-rank">{i + 1}</span>
                    <span className="board-name">{entry.username}</span>
                    <span className="muted">
                      {entry.value} {entry.unit}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </>
      )}
    </div>
  )
}
