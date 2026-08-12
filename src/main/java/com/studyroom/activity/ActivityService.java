package com.studyroom.activity;

import com.studyroom.friend.FriendRequest;
import com.studyroom.friend.FriendRequestRepository;
import com.studyroom.friend.FriendStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final FriendRequestRepository friendRequestRepository;

    public ActivityService(ActivityRepository activityRepository,
                           FriendRequestRepository friendRequestRepository) {
        this.activityRepository = activityRepository;
        this.friendRequestRepository = friendRequestRepository;
    }

    @Transactional
    public void record(Long userId, String username, String type, String text) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setUsername(username);
        activity.setType(type);
        activity.setText(text);
        activity.setCreatedAt(LocalDateTime.now());
        activityRepository.save(activity);
    }

    /** 首页信息流：自己和好友的最新动态。 */
    @Transactional(readOnly = true)
    public List<ActivityResponse> feed(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(userId);
        for (FriendRequest r : friendRequestRepository.findByStatusAndFromId(FriendStatus.ACCEPTED, userId)) {
            ids.add(r.getTo().getId());
        }
        for (FriendRequest r : friendRequestRepository.findByStatusAndToId(FriendStatus.ACCEPTED, userId)) {
            ids.add(r.getFrom().getId());
        }
        return activityRepository.findByUserIdInOrderByCreatedAtDesc(ids, PageRequest.of(0, 30)).stream()
                .map(ActivityResponse::from)
                .toList();
    }
}
