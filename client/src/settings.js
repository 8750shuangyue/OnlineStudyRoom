const KEY = 'study_room_settings'
const DEFAULTS = {
  focusMinutes: 0,
  breakMinutes: 5,
  autoRounds: 0,
  theme: 'dark',
  notifications: true,
  noiseType: 'brown',
  noiseVolume: 0.5
}

export function getSettings() {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || '{}') }
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveSettings(next) {
  localStorage.setItem(KEY, JSON.stringify(next))
}
