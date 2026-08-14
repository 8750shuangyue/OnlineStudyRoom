package com.studyroom.push;

import com.studyroom.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 浏览器 Web Push：VAPID 签名 + 负载加密（基于 nl.martijndwars:web-push）。
 * 发送在 @Async 线程执行，不阻塞业务接口；推送失败只记日志。
 */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private final PushSubscriptionRepository subscriptionRepository;

    @Value("${app.push.vapid.public-key}")
    private String vapidPublicKey;

    @Value("${app.push.vapid.private-key}")
    private String vapidPrivateKey;

    @Value("${app.push.subject:mailto:studyroom@localhost}")
    private String subject;

    public WebPushService(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public String vapidPublicKey() {
        return vapidPublicKey;
    }

    @Transactional
    public void subscribe(User user, String endpoint, String p256dh, String auth) {
        PushSubscription existing = subscriptionRepository
                .findByUserIdAndEndpoint(user.getId(), endpoint).orElse(null);
        if (existing != null) {
            existing.setP256dh(p256dh);
            existing.setAuth(auth);
            subscriptionRepository.save(existing);
            return;
        }
        PushSubscription sub = new PushSubscription();
        sub.setUser(user);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setCreatedAt(LocalDateTime.now());
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(User user, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(user.getId(), endpoint);
    }

    /** 向用户所有已订阅设备推送。 */
    @Async
    public void sendToUser(Long userId, String title, String body, String url) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = json(title, body, url);
        nl.martijndwars.webpush.PushService push = new nl.martijndwars.webpush.PushService();
        try {
            push.setPublicKey(vapidPublicKey);
            push.setPrivateKey(vapidPrivateKey);
            push.setSubject(subject);
        } catch (Exception e) {
            log.warn("WebPush 初始化失败: {}", e.getMessage());
            return;
        }
        for (PushSubscription sub : subscriptions) {
            try {
                nl.martijndwars.webpush.Subscription.Keys keys =
                        new nl.martijndwars.webpush.Subscription.Keys(sub.getP256dh(), sub.getAuth());
                nl.martijndwars.webpush.Subscription subscription =
                        new nl.martijndwars.webpush.Subscription(sub.getEndpoint(), keys);
                org.apache.http.HttpResponse response = push.send(
                        new nl.martijndwars.webpush.Notification(subscription, payload));
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    subscriptionRepository.deleteByUserIdAndEndpoint(userId, sub.getEndpoint());
                }
            } catch (Exception e) {
                log.warn("WebPush 发送失败 userId={} endpoint={}: {}",
                        userId, sub.getEndpoint(), e.getMessage());
            }
        }
    }

    private String json(String title, String body, String url) {
        return "{\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body)
                + "\",\"url\":\"" + escape(url) + "\"}";
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
