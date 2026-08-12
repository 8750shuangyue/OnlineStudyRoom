import { useCallback, useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import { api, getToken } from '../api.js'
import Modal from '../components/Modal.jsx'

export default function RagPage() {
  const [docs, setDocs] = useState([])
  const [question, setQuestion] = useState('')
  const [answer, setAnswer] = useState('')
  const [sources, setSources] = useState([])
  const [editingDoc, setEditingDoc] = useState(null)
  const [editForm, setEditForm] = useState({ name: '', category: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setDocs(await api('/api/documents'))
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function upload(file) {
    if (!file) return
    setError('')
    const fd = new FormData()
    fd.append('file', file)
    try {
      const resp = await fetch('/api/documents', {
        method: 'POST',
        headers: { Authorization: `Bearer ${getToken()}` },
        body: fd
      })
      if (!resp.ok) {
        let message = '上传失败'
        try {
          const data = await resp.json()
          if (data.message) message = data.message
        } catch {
          // 忽略
        }
        throw new Error(message)
      }
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function removeDoc(doc) {
    if (!window.confirm(`删除资料「${doc.name}」？`)) {
      return
    }
    setError('')
    try {
      await api(`/api/documents/${doc.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function saveDoc(e) {
    e.preventDefault()
    setError('')
    try {
      await api(`/api/documents/${editingDoc.id}`, {
        method: 'PUT',
        body: { name: editForm.name, category: editForm.category }
      })
      setEditingDoc(null)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function ask(e) {
    e.preventDefault()
    if (!question.trim() || busy) return
    setBusy(true)
    setError('')
    setAnswer('')
    setSources([])
    try {
      const data = await api('/api/ai/rag', { method: 'POST', body: { question } })
      setAnswer(data.answer)
      setSources(data.sources || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h2>资料问答</h2>
      <p className="muted">
        上传你的学习资料（txt / md / PDF / Word，≤5MB），AI 会基于资料回答并标注引用来源。
      </p>
      {error && <p className="error">{error}</p>}

      <div className="card">
        <h3>📄 我的资料 · {docs.length}</h3>
        <label className="upload-btn">
          ＋ 上传资料
          <input
            type="file"
            accept=".txt,.md,.markdown,.pdf,.docx,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(e) => {
              upload(e.target.files[0])
              e.target.value = ''
            }}
          />
        </label>
        {docs.length === 0 ? (
          <p className="muted">还没有资料，先上传一份吧。</p>
        ) : (
          docs.map((doc) => (
            <div className="friend-row" key={doc.id}>
              <span className="friend-name">
                📄 {doc.name}
                <span className="muted"> · {doc.charCount} 字</span>
                {doc.category && <span className="mini-chip">{doc.category}</span>}
              </span>
              <button
                className="btn tiny secondary"
                onClick={() => {
                  setEditingDoc(doc)
                  setEditForm({ name: doc.name, category: doc.category || '' })
                }}
              >
                编辑
              </button>
              <button className="btn tiny danger ghost" onClick={() => removeDoc(doc)}>
                删除
              </button>
            </div>
          ))
        )}
      </div>

      <div className="card">
        <h3>💬 基于资料提问</h3>
        <form className="inline-form" onSubmit={ask}>
          <input
            placeholder="例如：导数的定义是什么？"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            maxLength={500}
          />
          <button disabled={!question.trim() || busy}>{busy ? '思考中...' : '提问'}</button>
        </form>
        {answer && (
          <div className="rag-answer md">
            {sources.length > 0 && (
              <div className="rag-sources">
                <span>引用来源：</span>
                {sources.map((s) => (
                  <span className="tag-chip" key={s}>
                    📄 {s}
                  </span>
                ))}
              </div>
            )}
            <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
              {answer}
            </ReactMarkdown>
          </div>
        )}
      </div>

      {editingDoc && (
        <Modal title="编辑资料" onClose={() => setEditingDoc(null)}>
          <form className="modal-form" onSubmit={saveDoc}>
            <label>
              资料名称 *
              <input
                value={editForm.name}
                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                maxLength={200}
              />
            </label>
            <label>
              分类（如：数学 / 英语）
              <input
                value={editForm.category}
                onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                maxLength={50}
              />
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setEditingDoc(null)}>
                取消
              </button>
              <button disabled={!editForm.name.trim()}>保存</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
