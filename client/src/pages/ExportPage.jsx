import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, getToken } from '../api.js'

const TITLES = { notes: '我的笔记', mistakes: '我的错题', report: '学习报告' }

function fmtDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

export default function ExportPage() {
  const { type } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  async function downloadCsv() {
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

  useEffect(() => {
    let alive = true
    async function load() {
      try {
        let d
        if (type === 'notes') {
          d = await api('/api/notes')
        } else if (type === 'mistakes') {
          d = await api('/api/mistakes')
        } else if (type === 'report') {
          const [stats, sessions] = await Promise.all([
            api('/api/stats/me'),
            api('/api/sessions')
          ])
          d = { stats, sessions }
        } else {
          setError('未知导出类型')
          return
        }
        if (alive) setData(d)
      } catch (err) {
        if (alive) setError(err.message)
      }
    }
    load()
    return () => {
      alive = false
    }
  }, [type])

  return (
    <div className="export-page">
      <div className="row-between no-print">
        <h2>{TITLES[type] || '导出'}</h2>
        <div className="row">
          <Link className="btn secondary" to="/">
            返回
          </Link>
          <button className="btn" onClick={() => window.print()}>
            🖨️ 打印 / 导出 PDF
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {!data && !error && <p className="muted">加载中...</p>}

      {data && (
        <div className="print-doc">
          <h1>{TITLES[type] || '导出'}</h1>
          <p className="muted">网页版自习室 · 生成于 {new Date().toLocaleString('zh-CN')}</p>

          {type === 'notes' && (
            <div>
              {data.length === 0 && <p>暂无笔记</p>}
              {data.map((n) => (
                <section key={n.id}>
                  <h2>
                    {n.title || '无标题'}
                    {n.category ? `（${n.category}）` : ''}
                  </h2>
                  <pre>{n.content}</pre>
                </section>
              ))}
            </div>
          )}

          {type === 'mistakes' && (
            <div>
              {data.length === 0 && <p>暂无错题</p>}
              {data.map((m) => (
                <section key={m.id}>
                  <h2>
                    [{m.subject}] {m.question}
                  </h2>
                  {m.note && <pre>{m.note}</pre>}
                </section>
              ))}
            </div>
          )}

          {type === 'report' && data.stats && (
            <div>
              <section>
                <div className="row-between">
                  <h2>学习概况</h2>
                  <button className="btn tiny secondary no-print" onClick={downloadCsv}>
                    ⬇ 导出 CSV
                  </button>
                </div>
                <table className="print-table">
                  <tbody>
                    <tr>
                      <td>累计专注</td>
                      <td>{data.stats.totalMinutes} 分钟</td>
                    </tr>
                    <tr>
                      <td>专注次数</td>
                      <td>{data.stats.totalSessions} 次</td>
                    </tr>
                    <tr>
                      <td>连续打卡</td>
                      <td>{data.stats.streak} 天</td>
                    </tr>
                    <tr>
                      <td>累计 XP</td>
                      <td>
                        {data.stats.xp}（Lv.{data.stats.level}）
                      </td>
                    </tr>
                  </tbody>
                </table>
              </section>

              <section>
                <h2>最近专注记录</h2>
                {data.sessions.length === 0 && <p>暂无记录</p>}
                <table className="print-table">
                  <thead>
                    <tr>
                      <th>日期</th>
                      <th>房间</th>
                      <th>时长</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.sessions.slice(0, 50).map((s) => (
                      <tr key={s.id}>
                        <td>{fmtDate(s.startedAt)}</td>
                        <td>{s.roomName}</td>
                        <td>{Math.floor((s.durationSeconds || 0) / 60)} 分钟</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
