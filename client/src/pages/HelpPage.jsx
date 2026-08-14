import { Link } from 'react-router'

export default function HelpPage() {
  return (
    <div>
      <h2>使用指南</h2>
      <p className="muted">网页版自习室：和同伴一起开房间、专注计时、实时聊天、刷成就、问 AI。</p>

      <div className="card">
        <h3>🚀 快速开始</h3>
        <ol className="help-list">
          <li>注册/登录后，去 <Link to="/rooms">房间</Link> 创建或加入一个自习室</li>
          <li>进入房间点「开始专注」，默认 25 分钟，专注 ≥15 分钟才算一次有效专注</li>
          <li>完成专注、复习、签到会在首页「今日任务」领取 +10 XP</li>
        </ol>
      </div>

      <div className="card">
        <h3>🏠 房间玩法</h3>
        <ul className="help-list">
          <li>房间可设密码（私密房）、公告、每周目标（分钟）</li>
          <li>房主可以邀请、移除、禁言成员，转让房主</li>
          <li><b>组队专注</b>：2-6 人同时开始，全部结束后结算，完成可拿团队徽章</li>
          <li><b>周挑战</b>：房间每周目标达成时全员有进度提醒（周三）和结算通知（周日）</li>
          <li>实时聊天支持 @ 提醒成员，房主可开启 AI 房间助教</li>
        </ul>
      </div>

      <div className="card">
        <h3>🤖 AI 助手（<Link to="/chat">去聊天</Link>）</h3>
        <ul className="help-list">
          <li>普通对话/流式对话，支持连续追问</li>
          <li>上传资料后到 <Link to="/rag">资料问答</Link> 提问（支持 txt / md / PDF / Word，≤5MB）</li>
          <li>错题 AI 讲解、笔记自动摘要、生成知识卡片</li>
          <li>每日简报（早 7:30）、每周学习报告（周日）自动生成并推送到消息中心</li>
        </ul>
      </div>

      <div className="card">
        <h3>📚 学习工具</h3>
        <ul className="help-list">
          <li><Link to="/notes">笔记</Link>：Markdown 笔记 + 分类 + 导出 PDF / Markdown</li>
          <li><Link to="/mistakes">错题</Link>：按间隔重复计划复习，AI 讲解与变式题</li>
          <li><Link to="/cards">闪卡</Link>：知识卡片复习，到期提醒</li>
          <li><Link to="/stats">我的统计</Link>：专注热力图、周报、时段分析、排行榜</li>
          <li>首页「学习报告」可一键打印/导出 PDF</li>
        </ul>
      </div>

      <div className="card">
        <h3>🏅 成就与社交</h3>
        <ul className="help-list">
          <li>专注、连续打卡、赛季结算都会解锁徽章与称号</li>
          <li><Link to="/friends">好友</Link>：加好友、看动态；好友上线会收到提醒</li>
          <li>公开名片：把自己的主页链接发给朋友（分享图可下载）</li>
          <li>浏览器可安装为 App（PWA），支持推送通知（需 HTTPS 或 localhost）</li>
        </ul>
      </div>

      <div className="card">
        <h3>❓ 常见问题</h3>
        <ul className="help-list">
          <li><b>推送收不到？</b> 推送需要 HTTPS（或 localhost），且要在设置页手动开启</li>
          <li><b>AI 没反应？</b> 确认 DeepSeek Key 已配置（服务器部署时）</li>
          <li><b>想备份数据？</b> 设置页点「备份数据」，服务器每天凌晨自动备份</li>
        </ul>
      </div>
    </div>
  )
}
