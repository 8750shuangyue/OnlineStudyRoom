import { useState } from 'react'

const ONBOARDING_KEY = 'study_room_onboarded'

const STEPS = [
  {
    icon: '🏠',
    title: '找个房间，开始专注',
    desc: '创建或加入一个自习室房间，和同伴一起专注计时、实时聊天。'
  },
  {
    icon: '🤖',
    title: 'AI 助手随时待命',
    desc: '普通聊天、错题讲解、学习计划、资料问答（RAG），学习问题交给 AI。'
  },
  {
    icon: '🏅',
    title: '坚持打卡，攒成就',
    desc: '每日目标、连续打卡、徽章称号；消息中心统一处理 @提及、邀请和好友请求。'
  }
]

export function shouldShowOnboarding() {
  return !localStorage.getItem(ONBOARDING_KEY)
}

export function dismissOnboarding() {
  localStorage.setItem(ONBOARDING_KEY, '1')
}

export default function Onboarding({ onDone }) {
  const [step, setStep] = useState(0)
  const current = STEPS[step]

  function finish() {
    dismissOnboarding()
    onDone()
  }

  return (
    <div className="onboarding-overlay" onClick={finish}>
      <div className="onboarding-card" onClick={(e) => e.stopPropagation()}>
        <div className="onboarding-icon">{current.icon}</div>
        <h3>{current.title}</h3>
        <p className="muted">{current.desc}</p>
        <div className="onboarding-dots">
          {STEPS.map((_, i) => (
            <span key={i} className={`onboarding-dot ${i === step ? 'active' : ''}`} />
          ))}
        </div>
        <div className="row onboarding-actions">
          <button className="btn secondary" onClick={finish}>
            跳过
          </button>
          {step > 0 && (
            <button className="btn secondary" onClick={() => setStep((s) => s - 1)}>
              上一步
            </button>
          )}
          {step < STEPS.length - 1 ? (
            <button className="btn" onClick={() => setStep((s) => s + 1)}>
              下一步
            </button>
          ) : (
            <button className="btn" onClick={finish}>
              开始使用
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
