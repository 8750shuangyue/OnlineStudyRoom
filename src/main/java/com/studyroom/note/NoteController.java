package com.studyroom.note;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController extends CurrentUserSupport {

    private final NoteService noteService;

    public NoteController(NoteService noteService, UserRepository userRepository) {
        super(userRepository);
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteResponse> list(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String category,
                                   Authentication authentication) {
        return noteService.list(currentUser(authentication), search, category);
    }

    @GetMapping("/categories")
    public List<String> categories(Authentication authentication) {
        return noteService.categories(currentUser(authentication));
    }

    /** 导出全部笔记为 Markdown 文件。 */
    @GetMapping(value = "/export.md", produces = "text/markdown;charset=UTF-8")
    public String exportMarkdown(Authentication authentication) {
        return noteService.exportMarkdown(currentUser(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@Valid @RequestBody NoteRequest request, Authentication authentication) {
        return noteService.create(currentUser(authentication), request);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@PathVariable Long id,
                               @Valid @RequestBody NoteRequest request,
                               Authentication authentication) {
        return noteService.update(currentUser(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        noteService.delete(currentUser(authentication), id);
    }

}
