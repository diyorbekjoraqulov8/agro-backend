package uz.app.agro.irrigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IrrigationEventRepository extends JpaRepository<IrrigationEventEntity, Long> {

    List<IrrigationEventEntity> findTop20ByFieldIdOrderByIrrigatedOnDescIdDesc(Long fieldId);
}
