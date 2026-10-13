package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.monitoring.dto.FieldMonitoringResponse;
import uz.app.agro.monitoring.dto.MonitoringEventResponse;
import uz.app.agro.rbac.Permissions;

/** Admin: dala monitoringi (navbat holati + tarix) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoringService {

    private final FieldRepository fields;
    private final FieldMonitoringRepository queue;
    private final MonitoringEventRepository events;

    @PreAuthorize(Permissions.CAN_READ_FIELD)
    public FieldMonitoringResponse forField(Long fieldId) {
        if (!fields.existsById(fieldId)) throw new NotFoundException("Dala", fieldId);
        var status = queue.findById(fieldId).map(m -> new FieldMonitoringResponse.Status(m.getLastSceneDate(), m.getLastNdvi(),
                m.getNextCheckAt(), m.getLastCheckedAt(), m.getFailures())).orElse(null);
        return new FieldMonitoringResponse(status, events.findTop20ByFieldIdOrderBySceneDateDesc(fieldId).stream()
                .map(MonitoringService::toResponse).toList());
    }

    static MonitoringEventResponse toResponse(MonitoringEventEntity e) {
        return new MonitoringEventResponse(e.getId(), e.getSceneDate(), e.getRefDate(), e.getNdvi(), e.getNdviRef(), e.getDeclinePct(),
                e.getDeclineSide(), e.getImprovePct(), e.getVerdict(), e.getMapFileHashId(), e.getCreatedDate());
    }
}
