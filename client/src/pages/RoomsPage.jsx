import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useUnread } from '../useUnread.js'
import Modal from '../components/Modal.jsx'

const EMPTY_FORM = { name: '', category: '', password: '', announcement: '', focusMinutes: '', breakMinutes: '' }

export default function RoomsPage() {
  const { user } = useAuth()
  const { unreads } = useUnread()
  const [rooms, setRooms] = useState([])
  const [mine, setMine] = useState([])
  const [categories, setCategories] = useState([])
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState(EMPTY_FORM)
  const [editRoom, setEditRoom] = useState(null)
  const [editForm, setEditForm] = useState(EMPTY_FORM)
  const [editClearPassword, setEditClearPassword] = useState(false)
  const [joinTarget, setJoinTarget] = useState(null)
  const [joinPassword, setJoinPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams()
      if (search.trim()) params.set('search', search.trim())
      if (category) params.set('category', category)
      const qs = params.toString()
      const [roomList, myList, cats] = await Promise.all([
        api(`/api/rooms${qs ? `?${qs}` : ''}`),
        api('/api/rooms/mine'),
        api('/api/rooms/categories')
      ])
      setRooms(roomList)
      setMine(myList)
      setCategories(cats)
    } catch (err) {
      setError(err.message)
    }
  }, [search, category])

  useEffect(() => {
    load()
  }, [load])

  const isMember = (roomId) => mine.some((r) => r.id === roomId)
  const isOwner = (room) => room.ownerUsername === user?.username

  async function createRoom(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api('/api/rooms', {
        method: 'POST',
        body: {
          ...createForm,
          focusMinutes: createForm.focusMinutes ? Number(createForm.focusMinutes) : 0,
          breakMinutes: createForm.breakMinutes ? Number(createForm.breakMinutes) : 0
        }
      })
      setShowCreate(false)
      setCreateForm(EMPTY_FORM)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function updateRoom(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const body = {
        name: editForm.name,
        category: editForm.category,
        announcement: editForm.announcement,
        password: editClearPassword ? '' : editForm.password || null,
        focusMinutes: editForm.focusMinutes ? Number(editForm.focusMinutes) : 0,
        breakMinutes: editForm.breakMinutes ? Number(editForm.breakMinutes) : 0
      }
      await api(`/api/rooms/${editRoom.id}`, { method: 'PUT', body })
      setEditRoom(null)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function startJoin(room) {
    if (room.hasPassword) {
      setJoinPassword('')
      setJoinTarget(room)
      return
    }
    doJoin(room.id, null)
  }

  async function doJoin(roomId, password) {
    setBusy(true)
    setError('')
    try {
      await api(`/api/rooms/${roomId}/join`, { method: 'POST', body: { password } })
      setJoinTarget(null)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function leave(roomId) {
    setError('')
    try {
      await api(`/api/rooms/${roomId}/leave`, { method: 'POST' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function deleteRoom(room) {
    if (!window.confirm(`确定解散「${room.name}」吗？解散后房间不可见（学习记录保留）。`)) {
      return
    }
    setError('')
    try {
      await api(`/api/rooms/${room.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <div className="row-between">
        <h2>自习室房间</h2>
        <button onClick={() => setShowCreate(true)}>＋ 创建房间</button>
      </div>

      <div className="search-row">
        <input
          className="search-input"
          placeholder="搜索房间名称..."
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

      {error && <p className="error">{error}</p>}

      {rooms.length === 0 ? (
        <div className="empty-state">
          <div className="empty-emoji">🏕️</div>
          <p className="muted">还没有匹配的房间</p>
          <button className="btn" onClick={() => setShowCreate(true)}>
            ＋ 创建第一个房间
          </button>
        </div>
      ) : (
        <div className="room-grid">
          {rooms.map((room) => (
            <div className="card room-card" key={room.id}>
              <div className="room-card-top">
                <Link to={`/rooms/${room.id}`} className="room-name">
                  {room.name}
                </Link>
                {unreads[String(room.id)] > 0 && (
                  <span className="unread-badge">{unreads[String(room.id)]}</span>
                )}
                {room.hasPassword && <span className="lock-badge">私密</span>}
              </div>
              <div className="room-meta">
                {room.category && <span className="mini-chip">{room.category}</span>}
                <span className="muted">
                  房主 {room.ownerUsername} · {room.memberCount} 人
                </span>
              </div>
              <div className="row room-actions">
                <Link className="btn" to={`/rooms/${room.id}`}>
                  进入
                </Link>
                {isMember(room.id) ? (
                  <button
                    className="btn secondary"
                    onClick={() => leave(room.id)}
                    disabled={isOwner(room)}
                    title={isOwner(room) ? '房主需先转移或解散房间' : ''}
                  >
                    退出
                  </button>
                ) : (
                  <button className="btn secondary" onClick={() => startJoin(room)}>
                    加入
                  </button>
                )}
                {isOwner(room) && (
                  <>
                    <button
                      className="btn secondary"
                      onClick={() => {
                        setEditRoom(room)
                        setEditForm({
                          name: room.name,
                          category: room.category || '',
                          password: '',
                          announcement: '',
                          focusMinutes: '',
                          breakMinutes: ''
                        })
                        setEditClearPassword(false)
                      }}
                    >
                      编辑
                    </button>
                    <button className="btn danger ghost" onClick={() => deleteRoom(room)}>
                      解散
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreate && (
        <Modal title="创建房间" onClose={() => setShowCreate(false)}>
          <form className="modal-form" onSubmit={createRoom}>
            <label>
              房间名称 *
              <input
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                maxLength={50}
                autoFocus
              />
            </label>
            <label>
              标签（如：考研 / 编程 / 英语）
              <input
                value={createForm.category}
                onChange={(e) => setCreateForm({ ...createForm, category: e.target.value })}
                maxLength={30}
              />
            </label>
            <label>
              加入密码（留空为公开房间）
              <input
                value={createForm.password}
                onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                maxLength={100}
              />
            </label>
            <label>
              单次专注时长（分钟，0 = 成员自己设置）
              <input
                type="number"
                min="0"
                max="180"
                value={createForm.focusMinutes}
                onChange={(e) => setCreateForm({ ...createForm, focusMinutes: e.target.value })}
              />
            </label>
            <label>
              休息时长（分钟，0 = 成员自己设置）
              <input
                type="number"
                min="0"
                max="60"
                value={createForm.breakMinutes}
                onChange={(e) => setCreateForm({ ...createForm, breakMinutes: e.target.value })}
              />
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setShowCreate(false)}>
                取消
              </button>
              <button disabled={busy || !createForm.name.trim()}>创建</button>
            </div>
          </form>
        </Modal>
      )}

      {editRoom && (
        <Modal title={`编辑「${editRoom.name}」`} onClose={() => setEditRoom(null)}>
          <form className="modal-form" onSubmit={updateRoom}>
            <label>
              房间名称 *
              <input
                value={editForm.name}
                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                maxLength={50}
              />
            </label>
            <label>
              标签
              <input
                value={editForm.category}
                onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                maxLength={30}
              />
            </label>
            <label>
              房间公告
              <textarea
                value={editForm.announcement}
                onChange={(e) => setEditForm({ ...editForm, announcement: e.target.value })}
                maxLength={1000}
                rows={3}
              />
            </label>
            <label>
              新加入密码（留空不修改）
              <input
                value={editForm.password}
                onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                maxLength={100}
              />
            </label>
            <label>
              单次专注时长（分钟，0 = 成员自己设置）
              <input
                type="number"
                min="0"
                max="180"
                value={editForm.focusMinutes}
                onChange={(e) => setEditForm({ ...editForm, focusMinutes: e.target.value })}
              />
            </label>
            <label>
              休息时长（分钟，0 = 成员自己设置）
              <input
                type="number"
                min="0"
                max="60"
                value={editForm.breakMinutes}
                onChange={(e) => setEditForm({ ...editForm, breakMinutes: e.target.value })}
              />
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={editClearPassword}
                onChange={(e) => setEditClearPassword(e.target.checked)}
              />
              设为公开房间（清除密码）
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setEditRoom(null)}>
                取消
              </button>
              <button disabled={busy || !editForm.name.trim()}>保存</button>
            </div>
          </form>
        </Modal>
      )}

      {joinTarget && (
        <Modal title={`加入「${joinTarget.name}」`} onClose={() => setJoinTarget(null)}>
          <form
            className="modal-form"
            onSubmit={(e) => {
              e.preventDefault()
              doJoin(joinTarget.id, joinPassword)
            }}
          >
            <p className="muted">该房间是私密的，需要密码才能加入。</p>
            <label>
              房间密码
              <input
                value={joinPassword}
                onChange={(e) => setJoinPassword(e.target.value)}
                maxLength={100}
                autoFocus
              />
            </label>
            <div className="modal-actions">
              <button type="button" className="btn secondary" onClick={() => setJoinTarget(null)}>
                取消
              </button>
              <button disabled={busy || !joinPassword}>加入</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
