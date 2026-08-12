package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:wsdb;DB_CLOSE_DELAY=-1")
@AutoConfigureTestRestTemplate
class RealtimeTests {

    @Autowired
    private TestRestTemplate rest;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private String registerAndGetToken(String username) {
        String json = rest.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "secret123")), String.class).getBody();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void websocketChatAndPresence() throws Exception {
        String aliceToken = registerAndGetToken("alice");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(aliceToken);

        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "实时自习室"), headers), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        // 连接 WebSocket（token 与 roomId 通过 URL 参数传递）
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringJsonConverter());
        String url = "ws://localhost:" + port + "/ws?token=" + aliceToken + "&roomId=" + roomId;

        BlockingQueue<String> chatQueue = new LinkedBlockingQueue<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("roomId", String.valueOf(roomId));
        StompSession session = client.connectAsync(url, new StompSessionHandlerAdapter() {
        }, connectHeaders).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/rooms/" + roomId + "/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                chatQueue.offer((String) payload);
            }
        });

        // 发一条消息
        session.send("/app/rooms/" + roomId + "/send", "{\"content\":\"大家好，一起加油\"}");

        // 收到广播（JSON 字符串）
        String received = chatQueue.poll(5, TimeUnit.SECONDS);
        assertTrue(received != null && received.contains("大家好，一起加油"), "应收到聊天广播，实际：" + received);
        assertTrue(received != null && received.contains("alice"), "广播应包含发送者用户名");

        // 消息已持久化，历史接口可查到
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String history = rest.exchange("/api/rooms/" + roomId + "/messages",
                HttpMethod.GET, entity, String.class).getBody();
        assertTrue(history != null && history.contains("大家好，一起加油"));

        // 在线人数：等待 presence 事件处理完成后应 >= 1
        boolean onlineOk = false;
        for (int i = 0; i < 10; i++) {
            String online = rest.exchange("/api/rooms/" + roomId + "/online",
                    HttpMethod.GET, entity, String.class).getBody();
            if (online != null && online.contains("\"onlineCount\":1")) {
                onlineOk = true;
                break;
            }
            Thread.sleep(300);
        }
        assertTrue(onlineOk, "连接后在线人数应为 1");

        session.disconnect();
    }

    @Test
    void presenceClearedOnDisconnect() throws Exception {
        String aliceToken = registerAndGetToken("disco");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(aliceToken);

        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "断线测试房"), headers), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringJsonConverter());
        String url = "ws://localhost:" + port + "/ws?token=" + aliceToken + "&roomId=" + roomId;

        StompSession session = client.connectAsync(url, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);

        // 连接后在线人数为 1
        boolean online = false;
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        for (int i = 0; i < 10; i++) {
            String body = rest.exchange("/api/rooms/" + roomId + "/online",
                    HttpMethod.GET, entity, String.class).getBody();
            if (body != null && body.contains("\"onlineCount\":1")) {
                online = true;
                break;
            }
            Thread.sleep(300);
        }
        assertTrue(online, "连接后在线人数应为 1");

        // 断开后在线人数归零
        session.disconnect();
        boolean cleared = false;
        for (int i = 0; i < 10; i++) {
            String body = rest.exchange("/api/rooms/" + roomId + "/online",
                    HttpMethod.GET, entity, String.class).getBody();
            if (body != null && body.contains("\"onlineCount\":0")) {
                cleared = true;
                break;
            }
            Thread.sleep(300);
        }
        assertTrue(cleared, "断开后在线人数应为 0");
    }

    /**
     * 测试用转换器：发送 String 时按 text/plain 编码，接收任意文本负载时转为 String。
     * （服务端广播的是 application/json，默认 StringMessageConverter 不接收）
     */
    private static class StringJsonConverter implements MessageConverter {

        private final StringMessageConverter delegate = new StringMessageConverter();

        @Override
        public Object fromMessage(Message<?> message, Class<?> targetClass) {
            if (!String.class.equals(targetClass)) {
                return null;
            }
            Object payload = message.getPayload();
            if (payload instanceof byte[] bytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            if (payload instanceof String s) {
                return s;
            }
            return null;
        }

        @Override
        public Message<?> toMessage(Object payload, MessageHeaders headers) {
            // 发送走标准 StringMessageConverter，保留 STOMP 所需的消息头
            return delegate.toMessage(payload, headers);
        }
    }
}
