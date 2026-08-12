package com.studyroom.note;

import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(User user, String search, String category) {
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String cat = trimToNull(category);
        return noteRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(note -> cat == null || cat.equals(note.getCategory()))
                .filter(note -> keyword.isEmpty()
                        || (note.getTitle() != null && note.getTitle().toLowerCase(Locale.ROOT).contains(keyword))
                        || note.getContent().toLowerCase(Locale.ROOT).contains(keyword))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories(User user) {
        return noteRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(Note::getCategory)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportMarkdown(User user) {
        StringBuilder sb = new StringBuilder("# 我的笔记\n\n");
        for (NoteResponse note : list(user, null, null)) {
            sb.append("## ").append(note.title() == null ? "未命名笔记" : note.title()).append("\n\n");
            if (note.category() != null) {
                sb.append("分类：").append(note.category()).append("\n\n");
            }
            if (note.tags() != null && !note.tags().isEmpty()) {
                sb.append("标签：").append(String.join("、", note.tags())).append("\n\n");
            }
            sb.append(note.content()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    @Transactional
    public NoteResponse create(User user, NoteRequest request) {
        Note note = new Note();
        note.setUser(user);
        note.setTitle(trimToNull(request.title()));
        note.setCategory(trimToNull(request.category()));
        note.setTags(normalizeTags(request.tags()));
        note.setContent(request.content().trim());
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse update(User user, Long noteId, NoteRequest request) {
        Note note = getOwnedNote(user, noteId);
        note.setTitle(trimToNull(request.title()));
        note.setCategory(trimToNull(request.category()));
        note.setTags(normalizeTags(request.tags()));
        note.setContent(request.content().trim());
        note.setUpdatedAt(LocalDateTime.now());
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public void delete(User user, Long noteId) {
        noteRepository.delete(getOwnedNote(user, noteId));
    }

    private Note getOwnedNote(User user, Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));
        if (!note.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己的笔记");
        }
        return note;
    }

    private NoteResponse toResponse(Note note) {
        return new NoteResponse(note.getId(), note.getTitle(), note.getCategory(), note.getTags(),
                note.getContent(),
                note.getCreatedAt(), note.getUpdatedAt());
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(new LinkedHashSet<>());
        for (String tag : tags) {
            String trimmed = trimToNull(tag);
            if (trimmed != null && result.size() < 5) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
