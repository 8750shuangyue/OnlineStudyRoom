import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api.js'
import Modal from '../components/Modal.jsx'

export default function TasksPage() {
  const navigate = useNavigate()
  const [tasks, setTasks] = useState([])
  const [title, setTitle] = useState('')
  const [rooms, setRooms] = useState([])
  const [focusTask, setFocusTask] = useState(null)
  const [roomId, setRoomId] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setTasks(await api('/api/tasks'))
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function addTask(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/tasks', { method: 'POST', body: { title } })
      setTitle('')
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function toggle(task) {
    setError('')
    try {
      await api(`/api/tasks/${task.id}`, { method: 'PUT', body: { done: !task.done } })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function edit(task) {
    const next = window.prompt('修改任务内容', task.title)
    if (!next || next.trim() === task.title) {
      return
    }
    setError('')
    try {
      await api(`/api/tasks/${task.id}`, { method: 'PUT', body: { title: next.trim() } })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function remove(task) {
    if (!window.confirm(`删除任务「${task.title}」？`)) {
      return
    }
    setError('')
    try {
      await api(`/api/tasks/${task.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function openFocus(task) {
    setError('')
    try {
      const mine = await api('/api/rooms/mine')
      setRooms(mine)
      setRoomId('')
      setFocusTask(task)
    } catch (err) {
      setError(err.message)
    }
  }

  async function startFocus(e) {
    e.preventDefault()
    if (!roomId) {
      return
    }
    setBusy(true)
    setError('')
    try {
      await api('/api/sessions/start', {
        method: 'POST',
        body: { roomId: Number(roomId), taskId: focusTask.id }
      })
      navigate(`/rooms/${roomId}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const pending = tasks.filter((t) => !t.done)
  const done = tasks.filter((t) => t.done)

  return (
    <div>
      <h2>我的任务清单</h2>
      {error && <p className="error">{error}</p>}

      <form className="card inline-form" onSubmit={addTask}>
        <input
          placeholder="写下今天要完成的事..."
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
        />
        <button disabled={!title.trim()}>添加任务</button>
      </form>

      <div className="card">
        <h3>进行中 · {pending.length}</h3>
        {pending.length === 0 ? (
          <p className="muted">没有待办任务，轻松一下 ☕</p>
        ) : (
          pending.map((task) => (
            <div className="task-row" key={task.id}>
              <button
                className={`task-check ${task.done ? 'checked' : ''}`}
                onClick={() => toggle(task)}
              >
                ○
              </button>
              <span className="task-title">{task.title}</span>
              <button className="btn tiny" onClick={() => openFocus(task)}>
                开始专注
              </button>
              <button className="btn tiny secondary" onClick={() => edit(task)}>
                ✏️
              </button>
              <button className="btn tiny danger ghost" onClick={() => remove(task)}>
                🗑️
              </button>
            </div>
          ))
        )}
      </div>

      {done.length > 0 && (
        <div className="card">
          <h3>已完成 · {done.length}</h3>
          {done.map((task) => (
            <div className="task-row done" key={task.id}>
              <button className="task-check checked" onClick={() => toggle(task)}>
                ✓
              </button>
              <span className="task-title">{task.title}</span>
              <button className="btn tiny secondary" onClick={() => edit(task)}>
                ✏️
              </button>
              <button className="btn tiny danger ghost" onClick={() => remove(task)}>
                🗑️
              </button>
            </div>
          ))}
        </div>
      )}

      {focusTask && (
        <Modal title={`专注「${focusTask.title}」`} onClose={() => setFocusTask(null)}>
          <form className="modal-form" onSubmit={startFocus}>
            <p className="muted">选择一个自习室开始专注，完成后任务自动打勾。</p>
            {rooms.length === 0 ? (
              <p className="error">你还没有加入任何房间，先去房间页加入一个吧。</p>
            ) : (
              <label>
                选择房间
                <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
                  <option value="">请选择房间</option>
                  {rooms.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setFocusTask(null)}>
                取消
              </button>
              <button disabled={busy || !roomId}>开始</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
