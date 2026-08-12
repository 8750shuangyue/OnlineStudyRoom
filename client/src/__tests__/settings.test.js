import { beforeEach, describe, expect, it } from 'vitest'
import { getSettings, saveSettings } from '../settings.js'

describe('settings', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('返回默认设置', () => {
    expect(getSettings()).toEqual({
      focusMinutes: 0,
      breakMinutes: 5,
      autoRounds: 0,
      theme: 'dark',
      notifications: true,
      noiseType: 'brown',
      noiseVolume: 0.5
    })
  })

  it('保存并合并部分设置', () => {
    saveSettings({ breakMinutes: 25 })
    const s = getSettings()
    expect(s.breakMinutes).toBe(25)
    expect(s.focusMinutes).toBe(0)
    expect(s.notifications).toBe(true)
  })

  it('localStorage 损坏时回退默认值', () => {
    localStorage.setItem('study_room_settings', '{broken')
    expect(getSettings().breakMinutes).toBe(5)
  })
})
