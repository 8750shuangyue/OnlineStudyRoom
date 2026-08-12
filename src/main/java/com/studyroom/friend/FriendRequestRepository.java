package com.studyroom.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByStatusAndToId(FriendStatus status, Long toId);

    List<FriendRequest> findByStatusAndFromId(FriendStatus status, Long fromId);

    @Query("""
            select case when count(fr) > 0 then true else false end
            from FriendRequest fr
            where ((fr.from.id = :a and fr.to.id = :b) or (fr.from.id = :b and fr.to.id = :a))
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    @Query("""
            select case when count(fr) > 0 then true else false end
            from FriendRequest fr
            where fr.status = :status
              and ((fr.from.id = :a and fr.to.id = :b) or (fr.from.id = :b and fr.to.id = :a))
            """)
    boolean existsBetweenWithStatus(@Param("a") Long a, @Param("b") Long b,
                                    @Param("status") FriendStatus status);

    @Query("""
            select fr from FriendRequest fr
            where fr.status = com.studyroom.friend.FriendStatus.ACCEPTED
              and ((fr.from.id = :a and fr.to.id = :b) or (fr.from.id = :b and fr.to.id = :a))
            """)
    Optional<FriendRequest> findAcceptedBetween(@Param("a") Long a, @Param("b") Long b);
}
