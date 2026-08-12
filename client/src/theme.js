import { getSettings, saveSettings } from './settings.js'

function resolveTheme() {
  const settings = getSettings()
  if (settings.theme === 'system') {
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
  }
  return settings.theme === 'light' ? 'light' : 'dark'
}

export function applyTheme() {
  document.documentElement.dataset.theme = resolveTheme()
}

export function setTheme(theme) {
  saveSettings({ ...getSettings(), theme })
  applyTheme()
}

export function watchSystemTheme() {
  window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', applyTheme)
}
