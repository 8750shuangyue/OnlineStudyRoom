import { useRef, useState } from 'react'
import { api } from '../api.js'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

function StudyPlanCard() {
  const [goal, setGoal] = useState('')
  const [hours, setHours] = useState('')
  const [plan, setPlan] = useState('')
  const [planBusy, setPlanBusy] = useState(false)
  const [planError, setPlanError] = useState('')

  async function generate(e) {
    e.preventDefault()
    setPlanBusy(true)
    setPlanError('')
    setPlan('')
    try {
      const data = await api('/api/ai/study-plan', {
        method: 'POST',
        body: { goal, hoursPerDay: hours ? Number(hours) : null }
      })
      setPlan(data.plan)
    } catch (err) {
      setPlanError(err.message)
    } finally {
      setPlanBusy(false)
    }
  }

  return (
    <div className="card">
      <h3>📅 AI 学习计划生成</h3>
      <form className="inline-form plan-form" onSubmit={generate}>
        <input
          placeholder="目标，例如：一个月内搞定微积分基础"
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          maxLength={200}
        />
        <input
          type="number"
          min={1}
          max={16}
          placeholder="每天小时数"
          value={hours}
          onChange={(e) => setHours(e.target.value)}
          className="hours-input"
        />
        <button disabled={!goal.trim() || planBusy}>{planBusy ? '生成中...' : '生成计划'}</button>
      </form>
      {planError && <p className="error">{planError}</p>}
      {plan && (
        <div className="rag-answer md">
          <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
            {plan}
          </ReactMarkdown>
        </div>
      )}
    </div>
  )
}

export default function ChatPage() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [memoryMsg, setMemoryMsg] = useState('')
  const [listening, setListening] = useState(false)
  const recognitionRef = useRef(null)

  const hasSpeech =
    typeof window !== 'undefined' &&
    Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)

  async function submit(e) {
    e.preventDefault()
    const content = input.trim()
    if (!content || busy) {
      return
    }
    setBusy(true)
    setError('')
    setMessages((prev) => [...prev, { role: 'user', content }])
    setInput('')
    try {
      const data = await api('/api/ai/chat', {
        method: 'POST',
        body: { message: content }
      })
      setMessages((prev) => [...prev, { role: 'assistant', content: data.reply }])
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function clearMemory() {
    setError('')
    setMemoryMsg('')
    try {
      await api('/api/ai/clear-memory', { method: 'POST', body: { sessionKey: 'chat' } })
      setMemoryMsg('已清空对话记忆')
    } catch (err) {
      setError(err.message)
    }
  }

  function toggleVoice() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SR) {
      return
    }
    if (listening) {
      recognitionRef.current?.stop()
      setListening(false)
      return
    }
    const rec = new SR()
    rec.lang = 'zh-CN'
    rec.interimResults = false
    rec.onresult = (e) => {
      const text = Array.from(e.results)
        .map((r) => r[0].transcript)
        .join('')
      setInput((prev) => (prev ? `${prev}${text}` : text))
      setListening(false)
    }
    rec.onend = () => setListening(false)
    rec.onerror = () => setListening(false)
    recognitionRef.current = rec
    rec.start()
    setListening(true)
  }

  return (
    <div className="chat-page">
      <div className="row-between">
        <h2>AI 学习助手</h2>
        <button className="btn tiny secondary" onClick={clearMemory}>
          清空记忆
        </button>
      </div>
      {memoryMsg && <p className="ok">{memoryMsg}</p>}
      {error && <p className="error">{error}</p>}
      <div className="card chat-list ai-chat">
        {messages.length === 0 && (
          <p className="muted">问 AI 任何学习问题，例如：帮我解释一下番茄工作法。</p>
        )}
        {messages.map((msg, i) => (
          <div className={`chat-item ${msg.role}`} key={i}>
            <span className="chat-user">{msg.role === 'user' ? '我' : 'AI'}</span>
            <div className="chat-content md">
              {msg.role === 'user' ? (
                msg.content
              ) : (
                <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                  {msg.content}
                </ReactMarkdown>
              )}
            </div>
          </div>
        ))}
        {busy && <p className="muted">AI 思考中...</p>}
      </div>
      <form className="inline-form" onSubmit={submit}>
        <input
          placeholder="输入问题..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={2000}
        />
        {hasSpeech && (
          <button
            type="button"
            className={`btn secondary mic-btn ${listening ? 'mic-active' : ''}`}
            onClick={toggleVoice}
            title={listening ? '停止录音' : '语音输入'}
            disabled={busy}
          >
            {listening ? '⏹ 聆听中' : '🎤 语音'}
          </button>
        )}
        <button disabled={!input.trim() || busy}>发送</button>
      </form>
      <StudyPlanCard />
    </div>
  )
}
