import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { clearToken } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useUnread } from '../useUnread.js'
import { useMessages } from '../useMessages.js'
import Onboarding, { shouldShowOnboarding } from './Onboarding.jsx'

export default function Layout() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const { total } = useUnread()
  const { total: totalMessages } = useMessages()
  const [installPrompt, setInstallPrompt] = useState(null)
  const [installed, setInstalled] = useState(false)
  const [showOnboarding, setShowOnboarding] = useState(() => shouldShowOnboarding())

  useEffect(() => {
    const onPrompt = (e) => {
      e.preventDefault()
      setInstallPrompt(e)
    }
    const onInstalled = () => {
      setInstalled(true)
      setInstallPrompt(null)
    }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  async function installApp() {
    if (!installPrompt) {
      return
    }
    installPrompt.prompt()
    await installPrompt.userChoice
    setInstallPrompt(null)
  }

  function logout() {
    clearToken()
    setUser(null)
    navigate('/login')
  }

  return (
    <div className="layout">
      <nav className="navbar">
        <span className="brand">✦ 自习室</span>
        <NavLink to="/" className={({ isActive }) => (isActive ? 'active' : '')} end>
          首页
        </NavLink>
        <NavLink to="/rooms" className={({ isActive }) => (isActive ? 'active' : '')}>
          房间
          {total > 0 && <span className="nav-badge">{total > 99 ? '99+' : total}</span>}
        </NavLink>
        <NavLink to="/messages" className={({ isActive }) => (isActive ? 'active' : '')}>
          消息
          {totalMessages > 0 && (
            <span className="nav-badge">{totalMessages > 99 ? '99+' : totalMessages}</span>
          )}
        </NavLink>
        <NavLink to="/chat" className={({ isActive }) => (isActive ? 'active' : '')}>
          AI 助手
        </NavLink>
        <NavLink to="/friends" className={({ isActive }) => (isActive ? 'active' : '')}>
          好友
        </NavLink>
        <NavLink to="/achievements" className={({ isActive }) => (isActive ? 'active' : '')}>
          成就
        </NavLink>
        <NavLink to="/tasks" className={({ isActive }) => (isActive ? 'active' : '')}>
          任务
        </NavLink>
        <NavLink to="/notes" className={({ isActive }) => (isActive ? 'active' : '')}>
          笔记
        </NavLink>
        <NavLink to="/rag" className={({ isActive }) => (isActive ? 'active' : '')}>
          资料问答
        </NavLink>
        <NavLink to="/mistakes" className={({ isActive }) => (isActive ? 'active' : '')}>
          错题
        </NavLink>
        <NavLink to="/stats" className={({ isActive }) => (isActive ? 'active' : '')}>
          我的统计
        </NavLink>
        <NavLink to="/settings" className={({ isActive }) => (isActive ? 'active' : '')}>
          设置
        </NavLink>
        <span className="spacer" />
        <Link className="username" to={`/users/${user?.username}`}>
          {user?.username}
        </Link>
        {installPrompt && !installed && (
          <button className="install-btn" onClick={installApp}>
            ⬇ 安装 App
          </button>
        )}
        <button className="link-btn" onClick={logout}>
          退出
        </button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
      {showOnboarding && <Onboarding onDone={() => setShowOnboarding(false)} />}
    </div>
  )
}
