/**
 * 统一轮询调度：页面可见时按固定间隔轮询，切到后台时暂停，回到前台立即刷新。
 * 供未读/消息/好友动态等只读轮询复用，减少后台无意义请求。
 */
export function schedulePolling(fn, intervalMs) {
  const onVisible = () => {
    if (document.visibilityState === 'visible') {
      fn()
    }
  }
  fn()
  const timer = setInterval(() => {
    if (!document.hidden) {
      fn()
    }
  }, intervalMs)
  document.addEventListener('visibilitychange', onVisible)
  return () => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisible)
  }
}
