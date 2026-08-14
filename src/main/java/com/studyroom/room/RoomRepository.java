package com.studyroom.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("""
            select r from Room r
            where r.active = true
              and (:search is null or lower(r.name) like lower(concat('%', :search, '%')))
              and (:category is null or r.category = :category)
            order by r.id desc
            """)
    List<Room> search(@Param("search") String search, @Param("category") String category);

    @Query("""
            select distinct r.category from Room r
            where r.active = true and r.category is not null and r.category <> ''
            order by r.category
            """)
    List<String> findDistinctCategories();

    List<Room> findByWeeklyGoalMinutesGreaterThan(Integer goal);

    Optional<Room> findByInviteCode(String inviteCode);
}
