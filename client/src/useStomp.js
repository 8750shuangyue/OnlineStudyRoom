import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import { getToken } from './api.js'

/**
 * 房间 WebSocket 连接：自动订阅聊天、在线人数、专注、同步与 @提及主题。
 */
export function useStomp(
  roomId,
  { username, onMessage, onPresence, onFocus, onSync, onMention } = {}
) {
  const clientRef = useRef(null)
  const handlersRef = useRef({ onMessage, onPresence, onFocus, onSync, onMention })

  useEffect(() => {
    handlersRef.current = { onMessage, onPresence, onFocus, onSync, onMention }
  })

  useEffect(() => {
    const token = getToken()
    if (!token || !roomId) {
      return undefined
    }
    const client = new Client({
      brokerURL: `ws://${window.location.host}/ws?token=${encodeURIComponent(token)}`,
      connectHeaders: { roomId: String(roomId) },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/rooms/${roomId}/chat`, (frame) => {
          try {
            handlersRef.current.onMessage?.(JSON.parse(frame.body))
          } catch {
            // 忽略无法解析的消息
          }
        })
        client.subscribe(`/topic/rooms/${roomId}/presence`, (frame) => {
          try {
            handlersRef.current.onPresence?.(JSON.parse(frame.body))
          } catch {
            // 忽略无法解析的消息
          }
        })
        client.subscribe(`/topic/rooms/${roomId}/focus`, (frame) => {
          try {
            handlersRef.current.onFocus?.(JSON.parse(frame.body))
          } catch {
            // 忽略无法解析的消息
          }
        })
        client.subscribe(`/topic/rooms/${roomId}/sync`, (frame) => {
          try {
            handlersRef.current.onSync?.(JSON.parse(frame.body))
          } catch {
            // 忽略无法解析的消息
          }
        })
        if (username) {
          client.subscribe(`/topic/mentions/${username}`, (frame) => {
            try {
              handlersRef.current.onMention?.(JSON.parse(frame.body))
            } catch {
              // 忽略无法解析的消息
            }
          })
        }
      }
    })
    client.activate()
    clientRef.current = client
    return () => {
      client.deactivate()
    }
  }, [roomId, username])

  return {
    send(content) {
      const client = clientRef.current
      if (client && client.connected) {
        client.publish({
          destination: `/app/rooms/${roomId}/send`,
          body: JSON.stringify({ content }),
          // 后端按 text/plain 字符串接收并自行解析
          headers: { 'content-type': 'text/plain' }
        })
      }
    }
  }
}
