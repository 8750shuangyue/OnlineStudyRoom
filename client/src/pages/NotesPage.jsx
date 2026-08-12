import { useCallback, useEffect, useState } from 'react'
import { api, getToken } from '../api.js'
import Modal from '../components/Modal.jsx'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const EMPTY_FORM = { title: '', category: '', tags: '', content: '' }

export default function NotesPage() {
  const [notes, setNotes] = useState([])
  const [categories, setCategories] = useState([])
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [editForm, setEditForm] = useState(EMPTY_FORM)
  const [error, setError] = useState('')
  const [aiModal, setAiModal] = useState(null)
  const [aiBusy, setAiBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams()
      if (search.trim()) params.set('search', search.trim())
      if (category) params.set('category', category)
      const qs = params.toString()
      const [list, cats] = await Promise.all([
        api(`/api/notes${qs ? `?${qs}` : ''}`),
        api('/api/notes/categories')
      ])
      setNotes(list)
      setCategories(cats)
    } catch (err) {
      setError(err.message)
    }
  }, [search, category])

  useEffect(() => {
    load()
  }, [load])

  function toBody(f) {
    return {
      title: f.title,
      category: f.category,
      tags: f.tags
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean),
      content: f.content
    }
  }

  async function addNote(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/notes', { method: 'POST', body: toBody(form) })
      setForm(EMPTY_FORM)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function saveEdit(e) {
    e.preventDefault()
    setError('')
    try {
      await api(`/api/notes/${editing.id}`, { method: 'PUT', body: toBody(editForm) })
      setEditing(null)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function removeNote(note) {
    if (!window.confirm('删除这条笔记？')) {
      return
    }
    setError('')
    try {
      await api(`/api/notes/${note.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function exportMarkdown() {
    setError('')
    try {
      const resp = await fetch('/api/notes/export.md', {
        headers: { Authorization: `Bearer ${getToken()}` }
      })
      if (!resp.ok) {
        throw new Error(`导出失败（HTTP ${resp.status}）`)
      }
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'study-notes.md'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err.message)
    }
  }

  async function aiOrganize(note) {
    setError('')
    setAiBusy(true)
    setAiModal({ note, summary: '', cards: [] })
    try {
      const [sum, cards] = await Promise.all([
        api(`/api/ai/notes/${note.id}/summarize`, { method: 'POST' }),
        api(`/api/ai/notes/${note.id}/cards`, { method: 'POST' })
      ])
      setAiModal({ note, summary: sum.summary, cards: cards.cards || [] })
    } catch (err) {
      setAiModal(null)
      setError(err.message)
    } finally {
      setAiBusy(false)
    }
  }

  function fillEdit(note) {
    setEditing(note)
    setEditForm({
      title: note.title || '',
      category: note.category || '',
      tags: (note.tags || []).join(', '),
      content: note.content
    })
  }

  return (
    <div>
      <div className="row-between">
        <h2>我的笔记</h2>
        <button className="btn secondary" onClick={exportMarkdown}>
          ⬇ 导出 Markdown
        </button>
      </div>
      {error && <p className="error">{error}</p>}

      <form className="card note-add" onSubmit={addNote}>
        <input
          placeholder="标题（可选）"
          value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })}
          maxLength={200}
        />
        <div className="row note-form-row">
          <input
            placeholder="分类（如：数学 / 英语）"
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value })}
            maxLength={50}
          />
          <input
            placeholder="标签，逗号分隔（最多 5 个）"
            value={form.tags}
            onChange={(e) => setForm({ ...form, tags: e.target.value })}
            maxLength={200}
          />
        </div>
        <textarea
          placeholder="随手记点什么...（支持 Markdown 和公式）"
          value={form.content}
          onChange={(e) => setForm({ ...form, content: e.target.value })}
          maxLength={5000}
          rows={3}
        />
        <button disabled={!form.content.trim()}>保存笔记</button>
      </form>

      <div className="search-row">
        <input
          className="search-input"
          placeholder="搜索笔记..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <div className="category-chips">
          <button
            className={`chip-btn ${category === '' ? 'active' : ''}`}
            onClick={() => setCategory('')}
          >
            全部
          </button>
          {categories.map((c) => (
            <button
              key={c}
              className={`chip-btn ${category === c ? 'active' : ''}`}
              onClick={() => setCategory(category === c ? '' : c)}
            >
              {c}
            </button>
          ))}
        </div>
      </div>

      {notes.length === 0 ? (
        <p className="muted">还没有笔记，写一条吧。</p>
      ) : (
        <div className="note-grid">
          {notes.map((note) => (
            <div className="card note-card" key={note.id}>
              {note.title && <div className="note-title">{note.title}</div>}
              <div className="note-meta-line">
                {note.category && <span className="mini-chip">{note.category}</span>}
                {(note.tags || []).map((tag) => (
                  <span className="tag-chip" key={tag}>
                    #{tag}
                  </span>
                ))}
              </div>
              <div className="note-content md">
                <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                  {note.content}
                </ReactMarkdown>
              </div>
              <div className="note-meta">
                <span className="muted">{formatTime(note.updatedAt)}</span>
                <div className="row">
                  <button className="btn tiny secondary" onClick={() => fillEdit(note)}>
                    编辑
                  </button>
                  <button
                    className="btn tiny secondary"
                    onClick={() => aiOrganize(note)}
                    disabled={aiBusy}
                  >
                    ✨ AI 整理
                  </button>
                  <button className="btn tiny danger ghost" onClick={() => removeNote(note)}>
                    删除
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <Modal title="编辑笔记" onClose={() => setEditing(null)}>
          <form className="modal-form" onSubmit={saveEdit}>
            <label>
              标题
              <input
                value={editForm.title}
                onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                maxLength={200}
              />
            </label>
            <label>
              分类
              <input
                value={editForm.category}
                onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                maxLength={50}
              />
            </label>
            <label>
              标签（逗号分隔）
              <input
                value={editForm.tags}
                onChange={(e) => setEditForm({ ...editForm, tags: e.target.value })}
                maxLength={200}
              />
            </label>
            <label>
              内容
              <textarea
                value={editForm.content}
                onChange={(e) => setEditForm({ ...editForm, content: e.target.value })}
                maxLength={5000}
                rows={8}
              />
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setEditing(null)}>
                取消
              </button>
              <button disabled={!editForm.content.trim()}>保存</button>
            </div>
          </form>
        </Modal>
      )}

      {aiModal && (
        <Modal title={`✨ AI 整理：${aiModal.note.title || '未命名笔记'}`} onClose={() => setAiModal(null)}>
          <div className="modal-form">
            {aiBusy && <p className="muted">AI 正在整理...</p>}
            {aiModal.summary && (
              <>
                <h4>📝 摘要</h4>
                <div className="md">
                  <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                    {aiModal.summary}
                  </ReactMarkdown>
                </div>
              </>
            )}
            {aiModal.cards.length > 0 && (
              <>
                <h4>🃏 知识点卡片</h4>
                <div className="card-grid">
                  {aiModal.cards.map((card, i) => (
                    <div className="note-card card" key={i}>
                      <div className="flashcard-front">{card.front}</div>
                      <details>
                        <summary>查看背面</summary>
                        <div className="flashcard-back">{card.back}</div>
                      </details>
                      <div className="row note-meta">
                        <button
                          className="btn tiny"
                          disabled={card.added}
                          onClick={async () => {
                            try {
                              await api('/api/cards', {
                                method: 'POST',
                                body: {
                                  front: card.front,
                                  back: card.back,
                                  sourceType: 'NOTE',
                                  sourceId: aiModal.note.id
                                }
                              })
                              setAiModal((m) => ({
                                ...m,
                                cards: m.cards.map((c, ci) => (ci === i ? { ...c, added: true } : c))
                              }))
                            } catch (err) {
                              setError(err.message)
                            }
                          }}
                        >
                          {card.added ? '✓ 已加入' : '➕ 加入复习'}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
            <div className="modal-actions">
              <button className="btn secondary" onClick={() => setAiModal(null)}>
                关闭
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
