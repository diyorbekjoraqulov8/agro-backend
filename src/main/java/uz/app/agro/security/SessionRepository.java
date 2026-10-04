package uz.app.agro.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshTokenHash(String hash);

    Optional<Session> findByPreviousRefreshTokenHash(String hash);

    @Query("SELECT COUNT(s) > 0 FROM Session s " +
            "WHERE s.id = :id AND s.revokedAt IS NULL AND s.expiresAt > :now")
    boolean isActive(@Param("id") UUID id, @Param("now") LocalDateTime now);

    List<Session> findAllByUserIdAndRevokedAtIsNull(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Session s SET s.revokedAt = :now " +
            "WHERE  s.id = :sessionId AND s.revokedAt IS NULL")
    void revokeById(@Param("sessionId") UUID sessionId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Session s SET s.revokedAt = :now " +
            "WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<Session> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedDateDesc(
            Long userId, LocalDateTime now);
}