package com.studyroom.common;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * 控制器公共基类：统一从认证信息解析当前登录用户。
 */
public abstract class CurrentUserSupport {

    private final UserRepository userRepository;

    protected CurrentUserSupport(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    protected User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
