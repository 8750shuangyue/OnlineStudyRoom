package com.studyroom.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByUserIdInOrderByCreatedAtDesc(Collection<Long> userIds, Pageable pageable);
}
