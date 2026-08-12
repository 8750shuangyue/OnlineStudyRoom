package com.studyroom.ai;

import com.studyroom.study.StudySession;
import com.studyroom.document.Document;
import com.studyroom.document.DocumentRepository;
import com.studyroom.document.VectorSearchService;
import com.studyroom.document.VectorSearchService.ChunkHit;
import com.studyroom.mistake.Mistake;
import com.studyroom.user.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.format.DateTimeFormatter;

@Service
public class AiService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final VectorSearchService vectorSearchService;

    public AiService(ChatClient.Builder builder,
                     DocumentRepository documentRepository,
                     VectorSearchService vectorSearchService) {
        this.chatClient = builder.build();
        this.documentRepository = documentRepository;
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * 基于一次已完成的专注会话，让 AI 生成鼓励语和下一步建议。
     */
    public String summarizeStudy(StudySession session) {
        long minutes = Math.max(1, session.getDurationSeconds() / 60);
        String prompt = """
                你是一位温暖、具体的学习伙伴。
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
     * 轻量版 RAG：从用户上传的资料中按关键词检索片段，交给模型基于资料回答。
     */
    public RAGResult ragAnswer(User user, String question) {
        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (documents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先上传学习资料");
        }
        List<ChunkSource> chunkSources = new ArrayList<>();
        for (Document document : documents) {
            for (String text : chunk(document.getContent())) {
                chunkSources.add(new ChunkSource(document.getName(), text));
            }
        }
        List<String> chunkTexts = chunkSources.stream().map(ChunkSource::text).toList();
        List<ChunkHit> hits = vectorSearchService.searchTop(chunkTexts, question, 3);
        java.util.Map<String, String> textToName = new java.util.HashMap<>();
        for (ChunkSource source : chunkSources) {
            textToName.putIfAbsent(source.text(), source.documentName());
        }
        List<String> sources = hits.stream()
                .map(hit -> textToName.get(hit.chunk()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        String context = hits.stream()
                .map(ChunkHit::chunk)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("");
        String prompt = """
                你是学习助手。以下是从用户上传的学习资料中检索到的片段：

                %s

                请基于这些资料回答用户问题（用中文，适当使用 Markdown 排版）。
                如果资料中没有相关信息，请明确说明“资料中没有找到”。

                问题：%s
                """.formatted(context, question);
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return new RAGResult(answer, sources);
    }

    private record ChunkSource(String documentName, String text) {
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
