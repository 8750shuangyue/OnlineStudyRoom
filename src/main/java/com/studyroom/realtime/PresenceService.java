package com.studyroom.realtime;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间在线人数（内存态，进程重启即清零，适合开发阶段）。
 */
@Service
public class PresenceService {

    private final Map<Long, Set<String>> roomUsers = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> globalOnline = new ConcurrentHashMap<>();

    public record SessionInfo(Long roomId, String username) {
    }

    public void registerSession(String sessionId, Long roomId, String username) {
        sessions.put(sessionId, new SessionInfo(roomId, username));
    }

    public SessionInfo removeSession(String sessionId) {
        return sessions.remove(sessionId);
    }

    public synchronized int join(Long roomId, String username) {
        globalOnline.merge(username, 1, Integer::sum);
        return roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username)
                ? roomUsers.get(roomId).size()
                : roomUsers.get(roomId).size();
    }

    public synchronized int leave(Long roomId, String username) {
        Set<String> users = roomUsers.get(roomId);
        if (users == null) {
            return 0;
        }
        users.remove(username);
        globalOnline.computeIfPresent(username, (k, v) -> v <= 1 ? null : v - 1);
        if (users.isEmpty()) {
            roomUsers.remove(roomId);
        }
        return roomUsers.containsKey(roomId) ? users.size() : 0;
    }

    public synchronized int onlineCount(Long roomId) {
        Set<String> users = roomUsers.get(roomId);
        return users == null ? 0 : users.size();
    }

    public synchronized List<String> onlineUsers(Long roomId) {
        Set<String> users = roomUsers.get(roomId);
        return users == null ? List.of() : new ArrayList<>(users);
    }

    public boolean isOnline(String username) {
        return globalOnline.containsKey(username);
    }
}
