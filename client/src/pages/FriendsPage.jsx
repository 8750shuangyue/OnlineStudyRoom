import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api.js'

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

export default function FriendsPage() {
  const navigate = useNavigate()
  const [friends, setFriends] = useState([])
  const [requests, setRequests] = useState([])
  const [invites, setInvites] = useState([])
  const [addName, setAddName] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    try {
      const [f, r, i] = await Promise.all([
        api('/api/friends'),
        api('/api/friends/requests'),
        api('/api/invites')
      ])
      setFriends(f)
      setRequests(r)
      setInvites(i)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function addFriend(e) {
    e.preventDefault()
    setError('')
    setMessage('')
    try {
      await api('/api/friends/requests', { method: 'POST', body: { username: addName } })
      setMessage(`已向 ${addName.trim()} 发送好友请求`)
      setAddName('')
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function respondRequest(id, action) {
    setError('')
    try {
      await api(`/api/friends/requests/${id}/${action}`, { method: 'POST' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function removeFriend(username) {
    if (!window.confirm(`确定删除好友 ${username} 吗？`)) {
      return
    }
    setError('')
    try {
      await api(`/api/friends/${encodeURIComponent(username)}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function respondInvite(invite, action) {
    setError('')
    try {
      await api(`/api/invites/${invite.id}/${action}`, { method: 'POST' })
      if (action === 'accept') {
        navigate(`/rooms/${invite.roomId}`)
        return
      }
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h2>好友</h2>
      {error && <p className="error">{error}</p>}
      {message && <p className="ok">{message}</p>}

      <form className="card inline-form friend-add" onSubmit={addFriend}>
        <input
          placeholder="输入用户名添加好友"
          value={addName}
          onChange={(e) => setAddName(e.target.value)}
          maxLength={50}
        />
        <button disabled={!addName.trim()}>添加</button>
      </form>

      {requests.length > 0 && (
        <div className="card">
          <h3>好友请求</h3>
          {requests.map((req) => (
            <div className="friend-row" key={req.id}>
              <span className="friend-name">{req.username}</span>
              <div className="row">
                <button className="btn tiny" onClick={() => respondRequest(req.id, 'accept')}>
                  接受
                </button>
                <button
                  className="btn tiny danger ghost"
                  onClick={() => respondRequest(req.id, 'reject')}
                >
                  拒绝
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="card">
        <h3>我的好友 · {friends.length}</h3>
        {friends.length === 0 ? (
          <p className="muted">还没有好友，先添加一个吧。</p>
        ) : (
          friends.map((f) => (
            <div className="friend-row" key={f.username}>
              <span className="friend-name">
                <span className={`online-dot ${f.online ? 'on' : ''}`} />
                <Link to={`/users/${encodeURIComponent(f.username)}`}>{f.username}</Link>
                {f.online && <span className="online-label">在线</span>}
              </span>
              <button className="btn tiny danger ghost" onClick={() => removeFriend(f.username)}>
                删除
              </button>
            </div>
          ))
        )}
      </div>

      <div className="card">
        <h3>房间邀请</h3>
        {invites.length === 0 ? (
          <p className="muted">没有待处理的房间邀请。</p>
        ) : (
          invites.map((invite) => (
            <div className="friend-row" key={invite.id}>
              <span className="friend-name">
                {invite.fromUsername} 邀请你加入「{invite.roomName}」
                <span className="muted"> · {formatTime(invite.createdAt)}</span>
              </span>
              <div className="row">
                <button className="btn tiny" onClick={() => respondInvite(invite, 'accept')}>
                  接受
                </button>
                <button
                  className="btn tiny danger ghost"
                  onClick={() => respondInvite(invite, 'reject')}
                >
                  忽略
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
