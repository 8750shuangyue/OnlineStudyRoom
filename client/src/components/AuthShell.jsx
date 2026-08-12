export default function AuthShell({ title, children }) {
  return (
    <div className="auth-wrap">
      <div className="aurora aurora-1" />
      <div className="aurora aurora-2" />
      <div className="aurora aurora-3" />
      <div className="auth-inner">
        <div className="auth-hero">
          <div className="brand-mark">自习室</div>
          <p className="tagline">和同伴一起，把专注变成习惯</p>
          <div className="chips">
            <span className="chip">实时陪伴</span>
            <span className="chip">AI 学习助手</span>
            <span className="chip">专注排行</span>
          </div>
        </div>
        <div className="card glass auth-card">
          <h1>{title}</h1>
          {children}
        </div>
      </div>
    </div>
  )
}
