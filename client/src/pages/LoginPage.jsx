import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, setTokens } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthShell from '../components/AuthShell.jsx'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()
  const { setUser } = useAuth()

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const data = await api('/api/auth/login', {
        method: 'POST',
        body: { username, password },
        auth: false
      })
      setTokens(data.token, data.refreshToken)
      setUser({ username: data.username })
      navigate('/rooms')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="欢迎回来">
      <form className="auth-form" onSubmit={submit}>
        {error && <p className="error">{error}</p>}
        <label>
          用户名
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          密码
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <button disabled={busy}>{busy ? '登录中...' : '登录'}</button>
        <p className="muted">
          还没有账号？<Link to="/register">去注册</Link>
        </p>
      </form>
    </AuthShell>
  )
}
