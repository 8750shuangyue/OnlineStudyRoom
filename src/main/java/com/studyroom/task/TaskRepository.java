package com.studyroom.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long userId, LocalDateTime from, LocalDateTime to);
}
