package uz.app.agro.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "roles")
    List<UserEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findWithPermissionsByEmail(String email);

    /** Support guruhidagi xodim — huquqlari bilan (javob shu xodim nomidan, @PreAuthorize tekshiradi) */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findWithPermissionsByTelegramUserId(Long telegramUserId);

    boolean existsByTelegramUserIdAndIdNot(Long telegramUserId, Long id);

    boolean existsByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
       UPDATE UserEntity u
          SET u.failedAttempts = u.failedAttempts + 1
        WHERE u.email = :email AND u.deleted = false
       """)
    int incrementFailedAttempts(@Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
       UPDATE UserEntity u
          SET u.lockedUntil = :until, u.failedAttempts = 0
        WHERE u.email = :email AND u.deleted = false
          AND u.failedAttempts >= :maxAttempts
       """)
    int lockIfExceeded(@Param("email") String email,
                       @Param("until") LocalDateTime until,
                       @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
       UPDATE UserEntity u
          SET u.failedAttempts = 0, u.lockedUntil = null
        WHERE u.email = :email AND u.deleted = false
          AND (u.failedAttempts > 0 OR u.lockedUntil IS NOT NULL)
       """)
    int resetFailedAttempts(@Param("email") String email);
}
