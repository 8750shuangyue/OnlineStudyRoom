package com.studyroom.document;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController extends CurrentUserSupport {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService, UserRepository userRepository) {
        super(userRepository);
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
                                   Authentication authentication) {
        return documentService.upload(currentUser(authentication), file);
    }

    @GetMapping
    public List<DocumentResponse> list(Authentication authentication) {
        return documentService.list(currentUser(authentication));
    }

    @GetMapping("/categories")
    public List<String> categories(Authentication authentication) {
        return documentService.categories(currentUser(authentication));
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id,
                                   @Valid @RequestBody DocumentUpdateRequest request,
                                   Authentication authentication) {
        return documentService.update(currentUser(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        documentService.delete(currentUser(authentication), id);
    }

}
