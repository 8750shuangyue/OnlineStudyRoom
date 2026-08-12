package com.studyroom.task;

import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(User user) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse create(User user, TaskRequest request) {
        Task task = new Task();
        task.setUser(user);
        task.setTitle(request.title().trim());
        task.setDone(false);
        task.setCreatedAt(LocalDateTime.now());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(User user, Long taskId, TaskUpdateRequest request) {
        Task task = getOwnedTask(user, taskId);
        if (request.title() != null) {
            task.setTitle(request.title().trim());
        }
        if (request.done() != null && request.done() != task.isDone()) {
            task.setDone(request.done());
            task.setCompletedAt(request.done() ? LocalDateTime.now() : null);
        }
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(User user, Long taskId) {
        taskRepository.delete(getOwnedTask(user, taskId));
    }

    public Task getOwnedTask(User user, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己的任务");
        }
        return task;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.isDone(),
                task.getCreatedAt(), task.getCompletedAt());
    }
}
