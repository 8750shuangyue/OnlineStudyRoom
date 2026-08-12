import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'

export default function FlashcardsPage() {
  const [cards, setCards] = useState([])
  const [dueCount, setDueCount] = useState(0)
  const [queue, setQueue] = useState([])
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const [done, setDone] = useState(0)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [list, count] = await Promise.all([
        api('/api/cards'),
        api('/api/cards/due-count')
      ])
      setCards(list)
      setDueCount(count.count || 0)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function startReview() {
    setError('')
    try {
      const due = await api('/api/cards/due')
      setQueue(due)
      setIndex(0)
      setFlipped(false)
      setDone(0)
    } catch (err) {
      setError(err.message)
    }
  }

  async function rate(rating) {
    const current = queue[index]
    try {
      await api(`/api/cards/${current.id}/review`, { method: 'POST', body: { rating } })
      if (index + 1 < queue.length) {
        setIndex((i) => i + 1)
        setFlipped(false)
      } else {
        setDone(queue.length)
        setQueue([])
        await load()
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function remove(card) {
    if (!window.confirm('删除这张卡片？')) {
      return
    }
    setError('')
    try {
      await api(`/api/cards/${card.id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  const current = queue[index]

  return (
    <div>
      <div className="row-between">
        <h2>知识卡片复习</h2>
        <button className="btn" onClick={startReview} disabled={dueCount === 0 || queue.length > 0}>
          开始复习{dueCount > 0 ? `（${dueCount} 张待复习）` : ''}
        </button>
      </div>
      {error && <p className="error">{error}</p>}

      {queue.length > 0 && current && (
        <div className="card review-stage">
          <p className="muted">
            第 {index + 1} / {queue.length} 张
          </p>
          <div
            className={`flashcard-review ${flipped ? 'flipped' : ''}`}
            onClick={() => setFlipped((f) => !f)}
          >
            <div className="flashcard-face">
              {flipped ? current.back : current.front}
            </div>
            <p className="muted">{flipped ? '再次点击翻回正面' : '点击查看答案'}</p>
          </div>
          {flipped && (
            <div className="row center-row">
              <button className="btn danger" onClick={() => rate('HARD')}>
                困难
              </button>
              <button className="btn secondary" onClick={() => rate('NORMAL')}>
                一般
              </button>
              <button className="btn" onClick={() => rate('EASY')}>
                简单
              </button>
            </div>
          )}
        </div>
      )}

      {done > 0 && queue.length === 0 && (
        <div className="card review-stage">
          <h3>🎉 本轮完成！复习了 {done} 张卡片</h3>
          <p className="muted">按记忆情况安排了下次复习时间。</p>
        </div>
      )}

      <div className="card">
        <h3>📇 全部卡片 · {cards.length}</h3>
        {cards.length === 0 ? (
          <p className="muted">还没有卡片，去「笔记 → ✨ AI 整理」把知识点卡片加入复习吧。</p>
        ) : (
          <div className="card-grid">
            {cards.map((card) => (
              <div className="note-card card" key={card.id}>
                <div className="flashcard-front">{card.front}</div>
                <div className="row note-meta-line">
                  {card.due ? (
                    <span className="mini-chip warn-chip">待复习</span>
                  ) : (
                    <span className="mini-chip">下次 {new Date(card.dueAt).toLocaleDateString('zh-CN')}</span>
                  )}
                  <span className="tag-chip">复习{card.reviewCount}次</span>
                </div>
                <details>
                  <summary>查看答案</summary>
                  <div className="flashcard-back">{card.back}</div>
                </details>
                <div className="row note-meta">
                  <button className="btn tiny danger ghost" onClick={() => remove(card)}>
                    删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
