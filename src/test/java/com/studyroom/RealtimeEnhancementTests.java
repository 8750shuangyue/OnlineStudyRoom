package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import com.studyroom.realtime.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实时增强：历史消息分页 + 未读消息数/标记已读。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:realtimeenhdb;DB_CLOSE_DELAY=-1")
@AutoConfigureTestRestTemplate
class RealtimeEnhancementTests {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ChatService chatService;

    private String registerAndGetToken(String username) {
        String json = rest.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "secret123")), String.class).getBody();
        return JsonPath.read(json, "$.token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void historyPaginationReturnsLatestPageThenOlder() {
        String token = registerAndGetToken("pagealice");
        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "分页房间"), bearer(token)), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();
        for (int i = 0; i < 60; i++) {
            chatService.send(roomId, "pagealice", "消息 " + i);
        }

        HttpEntity<Void> entity = new HttpEntity<>(bearer(token));
        String page1 = rest.exchange("/api/rooms/" + roomId + "/messages?limit=50",
                HttpMethod.GET, entity, String.class).getBody();
        assertEquals(50, ((List<?>) JsonPath.read(page1, "$.messages")).size());
        assertTrue((Boolean) JsonPath.read(page1, "$.hasMore"));
        long firstId = ((Number) JsonPath.read(page1, "$.messages[0].id")).longValue();
        long secondId = ((Number) JsonPath.read(page1, "$.messages[1].id")).longValue();
        assertTrue(firstId < secondId, "第一页应按时间正序返回");

        String page2 = rest.exchange("/api/rooms/" + roomId + "/messages?before=" + firstId + "&limit=50",
                HttpMethod.GET, entity, String.class).getBody();
        assertEquals(10, ((List<?>) JsonPath.read(page2, "$.messages")).size());
        assertFalse((Boolean) JsonPath.read(page2, "$.hasMore"));
    }

    @Test
    void unreadCountsAndMarkRead() {
        String aliceToken = registerAndGetToken("unreadalice");
        String bobToken = registerAndGetToken("unreadbob");

        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "未读房间"), bearer(aliceToken)), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        ResponseEntity<String> joinResp = rest.postForEntity("/api/rooms/" + roomId + "/join",
                new HttpEntity<>(bearer(bobToken)), String.class);
        assertEquals(HttpStatus.OK, joinResp.getStatusCode());

        chatService.send(roomId, "unreadalice", "第一条");
        chatService.send(roomId, "unreadalice", "第二条");
        chatService.send(roomId, "unreadalice", "第三条");

        String unreadJson = rest.exchange("/api/rooms/unread",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class).getBody();
        List<Map<String, Object>> unreads = JsonPath.read(unreadJson, "$");
        assertEquals(1, unreads.size());
        assertEquals(roomId, ((Number) unreads.get(0).get("roomId")).longValue());
        assertEquals(3, ((Number) unreads.get(0).get("count")).intValue());

        ResponseEntity<String> readResp = rest.postForEntity("/api/rooms/" + roomId + "/read",
                new HttpEntity<>(bearer(bobToken)), String.class);
        assertEquals(HttpStatus.NO_CONTENT, readResp.getStatusCode());

        String afterRead = rest.exchange("/api/rooms/unread",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class).getBody();
        assertEquals(0, ((List<?>) JsonPath.read(afterRead, "$")).size());
    }

    @Test
    void nonMemberCannotReadHistory() {
        String aliceToken = registerAndGetToken("histalice");
        String bobToken = registerAndGetToken("histbob");
        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "私密历史"), bearer(aliceToken)), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        ResponseEntity<String> resp = rest.exchange("/api/rooms/" + roomId + "/messages",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void mentionCreatesNotificationAndCanBeRead() {
        String aliceToken = registerAndGetToken("notifyalice");
        String bobToken = registerAndGetToken("notifybob");
        String roomJson = rest.postForEntity("/api/rooms",
                new HttpEntity<>(Map.of("name", "通知房间"), bearer(aliceToken)), String.class).getBody();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();
        rest.postForEntity("/api/rooms/" + roomId + "/join",
                new HttpEntity<>(bearer(bobToken)), String.class);

        chatService.send(roomId, "notifyalice", "你好 @notifybob 一起来");

        String listJson = rest.exchange("/api/notifications",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class).getBody();
        List<Map<String, Object>> notifications = JsonPath.read(listJson, "$");
        assertEquals(1, notifications.size());
        assertEquals("MENTION", notifications.get(0).get("type"));

        String unreadJson = rest.exchange("/api/notifications/unread-count",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class).getBody();
        assertEquals(1, ((Number) JsonPath.read(unreadJson, "$.count")).intValue());

        ResponseEntity<String> readResp = rest.postForEntity("/api/notifications/read-all",
                new HttpEntity<>(bearer(bobToken)), String.class);
        assertEquals(HttpStatus.NO_CONTENT, readResp.getStatusCode());

        String afterJson = rest.exchange("/api/notifications/unread-count",
                HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class).getBody();
        assertEquals(0, ((Number) JsonPath.read(afterJson, "$.count")).intValue());
    }
}
