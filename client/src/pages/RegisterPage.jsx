import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, setTokens } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthShell from '../components/AuthShell.jsx'

export default function RegisterPage() {
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
      const data = await api('/api/auth/register', {
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
    <AuthShell title="创建账号">
      <form className="auth-form" onSubmit={submit}>
        {error && <p className="error">{error}</p>}
        <label>
          用户名（2-50 个字符）
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          密码（至少 8 位，含字母和数字）
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <button disabled={busy}>{busy ? '注册中...' : '注册并登录'}</button>
        <p className="muted">
          已有账号？<Link to="/login">去登录</Link>
        </p>
      </form>
    </AuthShell>
  )
}
