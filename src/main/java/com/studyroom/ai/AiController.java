package com.studyroom.ai;

import com.studyroom.mistake.Mistake;
import com.studyroom.mistake.MistakeRepository;
import com.studyroom.study.StudySession;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 学习助手统一入口：普通对话、流式对话、专注总结、资料问答、错题讲解、学习计划。
 */
@RestController
@RequestMapping({"/api/ai", "/api/chat"})
public class AiController {

    private final AiService aiService;
    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final MistakeRepository mistakeRepository;
    private final ChatClient chatClient;

    public AiController(AiService aiService,
                        StudySessionRepository studySessionRepository,
                        UserRepository userRepository,
                        MistakeRepository mistakeRepository,
                        ChatClient.Builder builder) {
        this.aiService = aiService;
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.mistakeRepository = mistakeRepository;
        this.chatClient = builder.build();
    }

    /** 普通对话：返回完整回复。 */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        return new ChatResponse(reply);
    }

    /** 流式对话：SSE 打字机效果。 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        return chatClient.prompt()
                .user(request.message())
                .stream()
                .content();
    }

    /**
     * 为一次已结束的专注会话生成 AI 学习总结。
     */
    @PostMapping("/sessions/{id}/summary")
    @Transactional(readOnly = true)
    public Map<String, String> summary(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        StudySession session = studySessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能总结自己的会话");
        }
        if (session.getEndedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话尚未结束，无法总结");
        }
        return Map.of("summary", aiService.summarizeStudy(session));
    }

    /** 基于上传的资料问答（轻量版 RAG）。 */
    @PostMapping("/rag")
    public Map<String, Object> rag(@Valid @RequestBody RAGRequest request,
                                   Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        RAGResult result = aiService.ragAnswer(user, request.question());
        return Map.of("answer", result.answer(), "sources", result.sources());
    }

    /** 错题 AI 讲解。 */
    @PostMapping("/mistakes/{id}/explain")
    public Map<String, String> explainMistake(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        Mistake mistake = mistakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "错题不存在"));
        if (!mistake.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能讲解自己的错题");
        }
        return Map.of("explanation", aiService.explainMistake(mistake));
    }

    /** AI 学习计划生成。 */
    @PostMapping("/study-plan")
    public Map<String, String> studyPlan(@Valid @RequestBody StudyPlanRequest request,
                                         Authentication authentication) {
        userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return Map.of("plan", aiService.studyPlan(request.goal(), request.hoursPerDay()));
    }
}
