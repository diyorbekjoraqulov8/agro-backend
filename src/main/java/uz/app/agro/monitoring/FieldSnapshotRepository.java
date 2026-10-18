package uz.app.agro.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FieldSnapshotRepository extends JpaRepository<FieldSnapshotEntity, Long> {

    Optional<FieldSnapshotEntity> findFirstByFieldIdOrderByCreatedDateDesc(Long fieldId);

    @Modifying
    @Query("UPDATE FieldSnapshotEntity s SET s.telegramFileId = :fileId WHERE s.id = :id")
    int rememberTelegramId(@Param("id") Long id, @Param("fileId") String fileId);

    List<FieldSnapshotEntity> findByFileHashIdIn(Collection<String> hashIds);
}
