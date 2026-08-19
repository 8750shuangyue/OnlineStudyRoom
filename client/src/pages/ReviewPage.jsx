import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'

function fmtTime(iso) {
  return new Date(iso).toLocaleString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

export default function ReviewPage() {
  const [date, setDate] = useState(today)
  const [review, setReview] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      setReview(await api(`/api/stats/day?date=${date}`))
    } catch (err) {
      setError(err.message)
    }
  }, [date])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div>
      <div className="row-between">
        <h2>📖 学习日记</h2>
        <div className="row">
          <input
            type="date"
            value={date}
            max={today()}
            onChange={(e) => setDate(e.target.value)}
          />
          <Link className="btn secondary" to="/stats">
            返回统计
          </Link>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {!review && !error && <p className="muted">加载中...</p>}
      {review && (
        <>
          <div className="stats-grid">
            <div className="card stat-card">
              <div className="stat-num">{review.totalMinutes}</div>
              <div className="stat-label">专注分钟</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{review.sessions.length}</div>
              <div className="stat-label">专注次数</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{review.tasksDone}</div>
              <div className="stat-label">完成任务</div>
            </div>
            <div className="card stat-card">
              <div className="stat-num">{review.notesCreated}</div>
              <div className="stat-label">新建笔记</div>
            </div>
          </div>

          <div className="card">
            <h3>
              ⏱️ 专注记录{' '}
              {review.checkedIn && <span className="mini-chip ok-chip">✅ 已签到</span>}
            </h3>
            {review.sessions.length === 0 ? (
              <p className="muted">这一天没有专注记录</p>
            ) : (
              <ul className="review-session-list">
                {review.sessions.map((s) => (
                  <li key={s.id}>
                    <span>{fmtTime(s.startedAt)}</span>
                    <span className="muted">{s.roomName}</span>
                    <b>{Math.floor((s.durationSeconds || 0) / 60)} 分钟</b>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  )
}
