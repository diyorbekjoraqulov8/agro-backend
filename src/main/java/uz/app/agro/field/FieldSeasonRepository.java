package uz.app.agro.field;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FieldSeasonRepository extends JpaRepository<FieldSeasonEntity, Long> {

    Optional<FieldSeasonEntity> findByFieldIdAndStatus(Long fieldId, FieldSeasonEntity.Status status);

    java.util.List<FieldSeasonEntity> findByFieldIdOrderByIdDesc(Long fieldId);
}
