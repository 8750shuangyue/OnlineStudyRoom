import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import Onboarding from '../components/Onboarding.jsx'

describe('Onboarding', () => {
  it('三步引导并完成', () => {
    const onDone = vi.fn()
    render(<Onboarding onDone={onDone} />)

    expect(screen.getByText('找个房间，开始专注')).toBeInTheDocument()
    fireEvent.click(screen.getByText('下一步'))
    expect(screen.getByText('AI 助手随时待命')).toBeInTheDocument()
    fireEvent.click(screen.getByText('下一步'))
    fireEvent.click(screen.getByText('开始使用'))

    expect(onDone).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('study_room_onboarded')).toBe('1')
  })

  it('支持跳过', () => {
    const onDone = vi.fn()
    render(<Onboarding onDone={onDone} />)
    fireEvent.click(screen.getByText('跳过'))
    expect(onDone).toHaveBeenCalledTimes(1)
  })
})
