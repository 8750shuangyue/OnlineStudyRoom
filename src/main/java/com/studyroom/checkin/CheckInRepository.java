package com.studyroom.checkin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByUserIdAndDate(Long userId, LocalDate date);

    List<CheckIn> findByUserIdOrderByDateDesc(Long userId);

    long countByUserId(Long userId);
}
