package com.studyroom.ai;

import com.studyroom.document.Document;
import com.studyroom.document.DocumentRepository;
import com.studyroom.document.VectorSearchService;
import com.studyroom.document.VectorSearchService.ChunkHit;
import com.studyroom.mistake.Mistake;
import com.studyroom.note.Note;
import com.studyroom.study.StudySession;
import com.studyroom.user.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    public static final String KEY_CHAT = "chat";
    public static final String KEY_RAG = "rag";
    private static final int MEMORY_LIMIT = 20;

    private static final String CHAT_SYSTEM = """
            你是网页版自习室里的 AI 学习助手，帮助用户理解知识点、解答学习问题、制定学习计划。
            回答用中文，适当使用 Markdown 排版和公式。
            """;

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final VectorSearchService vectorSearchService;
    private final AiMemoryService aiMemoryService;

    public AiService(ChatClient.Builder builder,
                     DocumentRepository documentRepository,
                     VectorSearchService vectorSearchService,
                     AiMemoryService aiMemoryService) {
        this.chatClient = builder.build();
        this.documentRepository = documentRepository;
        this.vectorSearchService = vectorSearchService;
        this.aiMemoryService = aiMemoryService;
    }

    /** 带记忆的普通聊天。 */
    public String chatWithMemory(User user, String message) {
        return answer(user, KEY_CHAT, CHAT_SYSTEM, message);
    }

    /** 带记忆的流式聊天（SSE）。 */
    public Flux<String> chatStreamWithMemory(User user, String message) {
        String prompt = buildPrompt(user, KEY_CHAT, CHAT_SYSTEM, message);
        StringBuilder full = new StringBuilder();
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(full::append)
                .doOnComplete(() -> {
                    if (user != null) {
                        aiMemoryService.add(user.getId(), KEY_CHAT, "user", message);
                        aiMemoryService.add(user.getId(), KEY_CHAT, "assistant", full.toString());
                    }
                });
    }

    public void clearMemory(User user, String sessionKey) {
        aiMemoryService.clear(user.getId(), sessionKey);
    }

    /** AI 房间助教（带房间级记忆）。 */
    public String tutorAnswer(User user, Long roomId, String persona, String roomName,
                              String announcement, String members, String message) {
        String personaLine = (persona == null || persona.isBlank())
                ? "你是一位耐心、专业的 AI 助教，用中文回答房间成员的学习问题。"
                : persona;
        String system = """
                %s

                房间：%s
                房间公告：%s
                当前成员：%s
                """.formatted(personaLine, roomName,
                announcement == null || announcement.isBlank() ? "无" : announcement,
                members == null || members.isBlank() ? "未知" : members);
        return answer(user, "tutor-" + roomId, system, message);
    }

    /** 一次性提问（不写记忆），供简报/周报等服务使用。 */
    public String askOnce(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 为一次已结束的专注会话生成 AI 学习总结。
     */
    public String summarizeStudy(StudySession session) {
        long minutes = Math.max(1, session.getDurationSeconds() / 60);
        String prompt = """
                你是一位温暖、具体的学伴。
                用户刚刚在「%s」自习室完成了 %d 分钟的专注学习（%s 到 %s）。
                请用 100 字以内给出一段真诚的鼓励，并结合这段时长给出下一步学习建议。
                直接输出内容，不要用任何标题或列表符号。
                """.formatted(
                session.getRoom().getName(),
                minutes,
                session.getStartedAt().format(TIME_FORMAT),
                session.getEndedAt().format(TIME_FORMAT));
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 基于上传的资料问答（轻量版 RAG），带多轮记忆。
     */
    public RAGResult ragAnswer(User user, String question) {
        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (documents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先上传学习资料");
        }
        List<ChunkSource> chunkSources = new ArrayList<>();
        for (Document document : documents) {
            for (String text : chunk(document.getContent())) {
                chunkSources.add(new ChunkSource(document.getId(), document.getName(), text));
            }
        }
        List<String> chunkTexts = chunkSources.stream().map(ChunkSource::text).toList();
        List<ChunkHit> hits = vectorSearchService.searchTop(chunkTexts, question, 3);
        java.util.Map<String, ChunkSource> textToSource = new java.util.HashMap<>();
        for (ChunkSource source : chunkSources) {
            textToSource.putIfAbsent(source.text(), source);
        }
        List<Citation> sources = hits.stream()
                .map(hit -> textToSource.get(hit.chunk()))
                .filter(java.util.Objects::nonNull)
                .map(source -> new Citation(source.documentId(), source.documentName(),
                        snippet(source.text())))
                .distinct()
                .toList();
        String context = hits.stream()
                .map(ChunkHit::chunk)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("");
        String system = """
                你是学习助手。以下是从用户上传的学习资料中检索到的片段：

                %s

                请基于这些资料回答用户问题（用中文，适当使用 Markdown 排版）。
                如果资料中没有相关信息，请明确说明“资料中没有找到”。
                """.formatted(context);
        String prompt = buildPrompt(user, KEY_RAG, system, question);
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (user != null) {
            aiMemoryService.add(user.getId(), KEY_RAG, "user", question);
            aiMemoryService.add(user.getId(), KEY_RAG, "assistant", answer);
        }
        return new RAGResult(answer, sources);
    }

    private record ChunkSource(Long documentId, String documentName, String text) {
    }

    private String snippet(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 180) + "…";
    }

    /**
     * 错题讲解。
     */
    public String explainMistake(Mistake mistake) {
        String subject = mistake.getSubject() == null ? "未分类" : mistake.getSubject();
        String note = mistake.getNote() == null ? "无" : mistake.getNote();
        String prompt = """
                你是耐心的学习老师。学生有一道错题需要讲解：

                科目：%s
                题目：%s
                学生自己的笔记：%s

                请用中文讲解这道题：先分析解题思路，再给出清晰步骤，最后总结易错点。
                使用 Markdown 排版，包含必要的数学公式。
                """.formatted(subject, mistake.getQuestion(), note);
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 生成一周学习计划。
     */
    public String studyPlan(String goal, Integer hoursPerDay) {
        String hours = hoursPerDay == null ? "未指定" : hoursPerDay + " 小时";
        String prompt = """
                你是一位专业的学习规划师。请为用户生成一份一周学习计划：

                学习目标：%s
                每天可投入：%s

                要求：包含每日任务安排、时间分配建议（结合番茄工作法）、周中复盘节点、周末总结。
                使用 Markdown 排版，内容要具体可执行。
                """.formatted(goal, hours);
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** 根据错题生成同知识点变式题。 */
    public VariationResponse generateVariation(Mistake mistake) {
        String subject = mistake.getSubject() == null ? "未分类" : mistake.getSubject();
        String note = mistake.getNote() == null ? "无" : mistake.getNote();
        String prompt = """
                你是出题老师。根据下面的错题，出一道同知识点、难度相近但题干不同的变式题。
                必须严格按以下格式输出两行：
                题目：<变式题题干>
                答案：<简要解析与答案>

                原题科目：%s
                原题：%s
                学生笔记：%s
                """.formatted(subject, mistake.getQuestion(), note);
        String reply = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        String question = extractLine(reply, "题目：");
        String answer = extractAfter(reply, "答案：");
        return new VariationResponse(question == null ? reply : question, answer == null ? "" : answer);
    }

    /** 笔记摘要。 */
    public String summarizeNote(Note note) {
        String prompt = """
                请用 5 句话以内总结下面这篇笔记的核心内容，使用 Markdown 无序列表。

                笔记标题：%s
                笔记内容：
                %s
                """.formatted(
                note.getTitle() == null ? "未命名" : note.getTitle(),
                note.getContent());
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** 从笔记生成知识点卡片（正面/背面格式）。 */
    public List<KnowledgeCard> generateNoteCards(Note note) {
        String prompt = """
                根据下面的笔记生成 3-5 张知识点卡片，用于复习。
                每张卡片必须严格按以下格式，卡片之间用空行分隔：
                正面：<知识点/问题>
                背面：<答案/解释>

                笔记标题：%s
                笔记内容：
                %s
                """.formatted(
                note.getTitle() == null ? "未命名" : note.getTitle(),
                note.getContent());
        String reply = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return parseCards(reply);
    }

    private String answer(User user, String sessionKey, String system, String message) {
        String prompt = buildPrompt(user, sessionKey, system, message);
        String reply = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (user != null) {
            aiMemoryService.add(user.getId(), sessionKey, "user", message);
            aiMemoryService.add(user.getId(), sessionKey, "assistant", reply);
        }
        return reply;
    }

    private String buildPrompt(User user, String sessionKey, String system, String message) {
        StringBuilder sb = new StringBuilder(system).append("\n\n");
        if (user != null) {
            for (AiMemory memory : aiMemoryService.recent(user.getId(), sessionKey, MEMORY_LIMIT)) {
                sb.append("user".equals(memory.getRole()) ? "用户：" : "助手：")
                        .append(memory.getContent())
                        .append("\n");
            }
        }
        sb.append("用户：").append(message);
        return sb.toString();
    }

    private List<KnowledgeCard> parseCards(String text) {
        List<KnowledgeCard> cards = new ArrayList<>();
        String[] blocks = text.split("\\n\\s*\\n");
        for (String block : blocks) {
            String front = extractLine(block, "正面：");
            String back = extractAfter(block, "背面：");
            if (front != null && back != null) {
                cards.add(new KnowledgeCard(front, back));
            }
        }
        return cards;
    }

    private String extractLine(String text, String marker) {
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(marker)) {
                return trimmed.substring(marker.length()).trim();
            }
        }
        return null;
    }

    private String extractAfter(String text, String marker) {
        int idx = text.indexOf(marker);
        return idx < 0 ? null : text.substring(idx + marker.length()).trim();
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        int size = 500;
        int step = 400;
        for (int i = 0; i < text.length(); i += step) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }
}
