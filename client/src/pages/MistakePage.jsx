import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import Modal from '../components/Modal.jsx'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

const EMPTY = { subject: '', question: '', note: '' }

export default function MistakePage() {
  const [mistakes, setMistakes] = useState([])
  const [dueCount, setDueCount] = useState(0)
  const [form, setForm] = useState(EMPTY)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [reviewing, setReviewing] = useState(null)
  const [variation, setVariation] = useState(null)
  const [variationBusy, setVariationBusy] = useState(false)
  const [explaining, setExplaining] = useState({})
  const [loadingExplain, setLoadingExplain] = useState({})
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [list, count] = await Promise.all([
        api('/api/mistakes'),
        api('/api/mistakes/review-count')
      ])
      setMistakes(list)
      setDueCount(count.count || 0)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function save(e) {
    e.preventDefault()
    setError('')
    const body = {
      subject: form.subject,
      question: form.question,
      note: form.note
    }
    try {
      if (editing) {
        await api(`/api/mistakes/${editing.id}`, { method: 'PUT', body })
      } else {
        await api('/api/mistakes', { method: 'POST', body })
      }
      setShowForm(false)
      setEditing(null)
      setForm(EMPTY)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function remove(mistake) {
    if (!window.confirm('删除这道错题？')) {
      return
    }
    setError('')
    try {
      await api(`/api/mistakes/${mistake.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function explain(mistake) {
    setError('')
    setLoadingExplain((prev) => ({ ...prev, [mistake.id]: true }))
    try {
      const data = await api(`/api/ai/mistakes/${mistake.id}/explain`, { method: 'POST' })
      setExplaining((prev) => ({ ...prev, [mistake.id]: data.explanation }))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingExplain((prev) => ({ ...prev, [mistake.id]: false }))
    }
  }

  async function submitReview(mastered) {
    setError('')
    try {
      await api(`/api/mistakes/${reviewing.id}/review`, {
        method: 'POST',
        body: { mastered }
      })
      setReviewing(null)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function fetchVariation(mistake) {
    setError('')
    setVariationBusy(true)
    try {
      const data = await api(`/api/ai/mistakes/${mistake.id}/variation`, { method: 'POST' })
      setVariation(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setVariationBusy(false)
    }
  }

  function statusChip(m) {
    if (m.reviewStatus === 'MASTERED') {
      return <span className="mini-chip ok-chip">✓ 已掌握</span>
    }
    const due = m.nextReviewAt && new Date(m.nextReviewAt) <= new Date()
    if (due) {
      return <span className="mini-chip warn-chip">⚠ 待复习</span>
    }
    return <span className="mini-chip">学习中 · 复习{m.reviewCount}次</span>
  }

  return (
    <div>
      <div className="row-between">
        <h2>我的错题本</h2>
        <Link className="btn secondary" to="/export/mistakes">
          📄 导出 PDF
        </Link>
        <button
          onClick={() => {
            setForm(EMPTY)
            setEditing(null)
            setShowForm(true)
          }}
        >
          ＋ 收录错题
        </button>
      </div>
      {error && <p className="error">{error}</p>}

      {dueCount > 0 && (
        <div className="review-banner">
          <span>📌 今天有 {dueCount} 道错题到期待复习</span>
          <button
            className="btn tiny"
            onClick={() => {
              const due = mistakes.find(
                (m) => m.nextReviewAt && new Date(m.nextReviewAt) <= new Date()
              )
              if (due) setReviewing(due)
            }}
          >
            开始复习
          </button>
        </div>
      )}

      {mistakes.length === 0 ? (
        <p className="muted">还没有错题，把做错的题收进来，让 AI 帮你讲。</p>
      ) : (
        <div className="mistake-list">
          {mistakes.map((m) => (
            <div className="card mistake-card" key={m.id}>
              <div className="mistake-head">
                {m.subject && <span className="mini-chip">{m.subject}</span>}
                {statusChip(m)}
                <span className="muted">
                  收录于 {new Date(m.createdAt).toLocaleDateString('zh-CN')}
                </span>
              </div>
              <div className="mistake-question">{m.question}</div>
              {m.note && <div className="mistake-note">📝 {m.note}</div>}
              <div className="row">
                <button
                  className="btn tiny"
                  onClick={() => explain(m)}
                  disabled={loadingExplain[m.id]}
                >
                  {loadingExplain[m.id] ? '讲解中...' : '🤖 AI 讲解'}
                </button>
                <button
                  className="btn tiny secondary"
                  onClick={() => setReviewing(m)}
                  disabled={m.reviewStatus === 'MASTERED'}
                >
                  🔁 复习
                </button>
                <button
                  className="btn tiny secondary"
                  onClick={() => fetchVariation(m)}
                  disabled={variationBusy}
                >
                  {variationBusy ? '生成中...' : '🔀 变式题'}
                </button>
                <button
                  className="btn tiny secondary"
                  onClick={() => {
                    setEditing(m)
                    setForm({ subject: m.subject || '', question: m.question, note: m.note || '' })
                    setShowForm(true)
                  }}
                >
                  编辑
                </button>
                <button className="btn tiny danger ghost" onClick={() => remove(m)}>
                  删除
                </button>
              </div>
              {explaining[m.id] && (
                <div className="explanation md">
                  <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                    {explaining[m.id]}
                  </ReactMarkdown>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <Modal
          title={editing ? '编辑错题' : '收录错题'}
          onClose={() => {
            setShowForm(false)
            setEditing(null)
          }}
        >
          <form className="modal-form" onSubmit={save}>
            <label>
              科目（可选）
              <input
                value={form.subject}
                onChange={(e) => setForm({ ...form, subject: e.target.value })}
                maxLength={50}
              />
            </label>
            <label>
              题目 *
              <textarea
                value={form.question}
                onChange={(e) => setForm({ ...form, question: e.target.value })}
                maxLength={2000}
                rows={4}
              />
            </label>
            <label>
              我的思路 / 易错点（可选）
              <textarea
                value={form.note}
                onChange={(e) => setForm({ ...form, note: e.target.value })}
                maxLength={2000}
                rows={3}
              />
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>
                取消
              </button>
              <button disabled={!form.question.trim()}>保存</button>
            </div>
          </form>
        </Modal>
      )}

      {reviewing && (
        <Modal title="错题复习" onClose={() => setReviewing(null)}>
          <div className="modal-form">
            {reviewing.subject && <span className="mini-chip">{reviewing.subject}</span>}
            <div className="mistake-question">{reviewing.question}</div>
            {reviewing.note && (
              <div className="mistake-note">
                <details>
                  <summary>查看我的笔记</summary>
                  {reviewing.note}
                </details>
              </div>
            )}
            <p className="muted">这次做对了吗？做对会延后复习间隔，做错明天再战。</p>
            <div className="row modal-actions">
              <button className="btn danger" onClick={() => submitReview(false)}>
                还没掌握
              </button>
              <button className="btn" onClick={() => submitReview(true)}>
                掌握了
              </button>
            </div>
          </div>
        </Modal>
      )}

      {variation && (
        <Modal title="变式题" onClose={() => setVariation(null)}>
          <div className="modal-form">
            <div className="mistake-question">{variation.question}</div>
            <details className="variation-answer">
              <summary>查看答案与解析</summary>
              <div className="mistake-note">{variation.answer}</div>
            </details>
            <div className="modal-actions">
              <button className="btn secondary" onClick={() => setVariation(null)}>
                关闭
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
