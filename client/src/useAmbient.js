import { useEffect, useRef, useState } from 'react'

/**
 * 白噪音（Web Audio 实时合成，无需音频文件）
 * 雨声 = 棕色噪音 + 低通；粉噪 = 白噪音 + 低通；白噪 = 纯白噪音
 */
export function useWhiteNoise() {
  const ctxRef = useRef(null)
  const sourceRef = useRef(null)
  const gainRef = useRef(null)
  const [playing, setPlaying] = useState(false)
  const [volume, setVolume] = useState(0.5)

  function ensureCtx() {
    if (!ctxRef.current) {
      const Ctx = window.AudioContext || window.webkitAudioContext
      ctxRef.current = new Ctx()
    }
    return ctxRef.current
  }

  function start(type) {
    const ctx = ensureCtx()
    if (ctx.state === 'suspended') {
      ctx.resume()
    }
    stop()
    const size = ctx.sampleRate * 2
    const buffer = ctx.createBuffer(1, size, ctx.sampleRate)
    const data = buffer.getChannelData(0)
    if (type === 'brown') {
      let last = 0
      for (let i = 0; i < size; i++) {
        const white = Math.random() * 2 - 1
        last = (last + 0.02 * white) / 1.02
        data[i] = last * 3.5
      }
    } else {
      for (let i = 0; i < size; i++) {
        data[i] = Math.random() * 2 - 1
      }
    }
    const source = ctx.createBufferSource()
    source.buffer = buffer
    source.loop = true
    const gain = ctx.createGain()
    gain.gain.value = volume
    let node = source
    if (type === 'brown' || type === 'pink') {
      const filter = ctx.createBiquadFilter()
      filter.type = 'lowpass'
      filter.frequency.value = type === 'brown' ? 420 : 850
      node.connect(filter)
      node = filter
    }
    node.connect(gain)
    gain.connect(ctx.destination)
    source.start()
    sourceRef.current = source
    gainRef.current = gain
    setPlaying(true)
  }

  function stop() {
    if (sourceRef.current) {
      try {
        sourceRef.current.stop()
      } catch {
        // 已停止
      }
      sourceRef.current.disconnect()
      sourceRef.current = null
    }
    if (gainRef.current) {
      gainRef.current.disconnect()
      gainRef.current = null
    }
    setPlaying(false)
  }

  function changeVolume(value) {
    setVolume(value)
    if (gainRef.current) {
      gainRef.current.gain.value = value
    }
  }

  useEffect(
    () => () => {
      stop()
    },
    []
  )

  return { playing, volume, start, stop, setVolume: changeVolume }
}

/** 提示音 */
export function playBeep(freq = 880, duration = 0.25) {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.frequency.value = freq
    osc.type = 'sine'
    gain.gain.setValueAtTime(0.001, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.4, ctx.currentTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + duration)
    setTimeout(() => ctx.close(), duration * 1000 + 200)
  } catch {
    // 浏览器不支持时静默
  }
}

/** 浏览器通知 */
export async function requestNotifyPermission() {
  try {
    if (!('Notification' in window)) {
      return false
    }
    if (Notification.permission === 'granted') {
      return true
    }
    if (Notification.permission === 'denied') {
      return false
    }
    return (await Notification.requestPermission()) === 'granted'
  } catch {
    return false
  }
}

export function notify(title, body) {
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(title, { body })
    }
  } catch {
    // 忽略
  }
}

/** 标签页标题倒计时 */
export function usePageTitleCountdown() {
  useEffect(
    () => () => {
      document.title = '自习室'
    },
    []
  )
  return (text) => {
    document.title = text ? `${text} · 自习室` : '自习室'
  }
}
