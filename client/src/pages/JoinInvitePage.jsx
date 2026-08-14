import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function JoinInvitePage() {
  const { code } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) {
      return
    }
    let alive = true
    async function join() {
      setBusy(true)
      setError('')
      try {
        const room = await api('/api/rooms/join-by-code', { method: 'POST', body: { code } })
        if (alive) {
          navigate(`/rooms/${room.id}`, { replace: true })
        }
      } catch (err) {
        if (alive) {
          setError(err.message)
        }
      } finally {
        if (alive) {
          setBusy(false)
        }
      }
    }
    join()
    return () => {
      alive = false
    }
  }, [user, code, navigate])

  if (!user) {
    return (
      <div className="auth-wrap">
        <div className="card" style={{ maxWidth: 420, width: '100%' }}>
          <h2>加入自习室</h2>
          <p className="muted">
            你收到了一份房间邀请，需要登录后加入。登录后重新打开这条邀请链接即可。
          </p>
          <div className="row">
            <Link className="btn" to="/login">
              去登录
            </Link>
            <Link className="btn secondary" to="/register">
              注册账号
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-wrap">
      <div className="card" style={{ maxWidth: 420, width: '100%' }}>
        <h2>加入自习室</h2>
        {error && <p className="error">{error}</p>}
        {busy ? (
          <p className="muted">正在加入房间...</p>
        ) : (
          <p className="muted">正在通过邀请码加入...</p>
        )}
      </div>
    </div>
  )
}
