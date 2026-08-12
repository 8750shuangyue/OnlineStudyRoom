package com.studyroom.friend;

import com.studyroom.realtime.PresenceService;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.studyroom.activity.ActivityService;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final ActivityService activityService;

    public FriendService(FriendRequestRepository friendRequestRepository,
                         UserRepository userRepository,
                         PresenceService presenceService,
                         ActivityService activityService) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
        this.activityService = activityService;
    }

    @Transactional
    public FriendRequestResponse sendRequest(User from, String username) {
        User to = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (to.getId().equals(from.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能添加自己为好友");
        }
        if (friendRequestRepository.existsBetween(from.getId(), to.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发送过请求或已是好友");
        }
        FriendRequest request = new FriendRequest();
        request.setFrom(from);
        request.setTo(to);
        request.setStatus(FriendStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        request = friendRequestRepository.save(request);
        return new FriendRequestResponse(request.getId(), to.getUsername(), request.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> incomingRequests(User user) {
        return friendRequestRepository.findByStatusAndToId(FriendStatus.PENDING, user.getId()).stream()
                .map(r -> new FriendRequestResponse(r.getId(), r.getFrom().getUsername(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void acceptRequest(User user, Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请求不存在"));
        if (!request.getTo().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理发给自己的请求");
        }
        if (request.getStatus() != FriendStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求已处理");
        }
        request.setStatus(FriendStatus.ACCEPTED);
        friendRequestRepository.save(request);
        activityService.record(user.getId(), user.getUsername(), "FRIEND_ACCEPTED",
                "和 " + request.getFrom().getUsername() + " 成为了好友");
        activityService.record(request.getFrom().getId(), request.getFrom().getUsername(), "FRIEND_ACCEPTED",
                "和 " + user.getUsername() + " 成为了好友");
    }

    @Transactional
    public void rejectRequest(User user, Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请求不存在"));
        if (!request.getTo().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理发给自己的请求");
        }
        friendRequestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> friends(User user) {
        List<FriendResponse> result = new ArrayList<>();
        for (FriendRequest r : friendRequestRepository.findByStatusAndFromId(FriendStatus.ACCEPTED, user.getId())) {
            result.add(new FriendResponse(r.getTo().getUsername(), presenceService.isOnline(r.getTo().getUsername())));
        }
        for (FriendRequest r : friendRequestRepository.findByStatusAndToId(FriendStatus.ACCEPTED, user.getId())) {
            result.add(new FriendResponse(r.getFrom().getUsername(), presenceService.isOnline(r.getFrom().getUsername())));
        }
        return result;
    }

    @Transactional
    public void removeFriend(User user, String username) {
        User other = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        FriendRequest request = friendRequestRepository.findAcceptedBetween(user.getId(), other.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "你们还不是好友"));
        friendRequestRepository.delete(request);
    }

    public boolean areFriends(Long userId, String otherUsername) {
        User other = userRepository.findByUsername(otherUsername).orElse(null);
        return other != null
                && friendRequestRepository.existsBetweenWithStatus(userId, other.getId(), FriendStatus.ACCEPTED);
    }
}
