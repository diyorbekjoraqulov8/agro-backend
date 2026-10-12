package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FAQAT bot ("📋 Dalalarim"). @PreAuthorize YO'Q — controller'ga ULANMASIN.
 * Har metod telegramUserId bilan: callback_data ishonchsiz, dala SHU fermerniki bo'lishi shart —
 * begona dala "topilmadi" (IDOR, ADR #16).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldBotService {

    private final FieldRepository fieldRepository;
    private final AuditService auditService;

    public record FieldCard(long id, String name, Crop crop, BigDecimal areaHa,
                            Long lastJobId, String lastJobStatus, LocalDateTime lastJobAt, Long lastDoneJobId) {}

    public List<FieldCard> list(long telegramUserId) {
        List<FieldEntity> fields = fieldRepository.findByFarmerTelegramUserIdOrderByIdDesc(telegramUserId);
        if (fields.isEmpty()) return List.of();
        // Har dalaning oxirgi tahlili — BITTA so'rov (N+1 emas)
        Map<Long, FieldRepository.LastJobs> last = fieldRepository
                .lastJobs(fields.stream().map(FieldEntity::getId).toList()).stream()
                .collect(Collectors.toMap(FieldRepository.LastJobs::getFieldId, Function.identity()));
        return fields.stream().map(f -> card(f, last.get(f.getId()))).toList();
    }

    public Optional<FieldCard> card(long telegramUserId, long fieldId) {
        return owned(telegramUserId, fieldId).map(f -> card(f,
                fieldRepository.lastJobs(List.of(fieldId)).stream().findFirst().orElse(null)));
    }

    public record Boundary(String name, Crop crop, List<List<Number>> ring) {}

    /** Mini App'ni tahrirlash rejimida ochish uchun: nom, ekin va halqa [[lon, lat], ...] */
    @SuppressWarnings("unchecked")
    public Optional<Boundary> boundary(long telegramUserId, long fieldId) {
        return owned(telegramUserId, fieldId).map(f -> new Boundary(f.getName(), f.getCrop(),
                ((List<List<List<Number>>>) f.getBoundaryGeojson().get("coordinates")).getFirst()));
    }

    /** Almashlab ekish: dala chegarasi o'sha, ekin yangi (g'alladan keyin mosh/poliz) */
    @Transactional
    public Optional<String> setCrop(long telegramUserId, long fieldId, Crop crop) {
        return owned(telegramUserId, fieldId).map(f -> {
            Crop old = f.getCrop();
            f.setCrop(crop);
            auditService.event(AuditEvent.FIELD_UPDATED).target("FIELD", fieldId)
                    .detail("crop", old + " → " + crop).record();
            return f.getName();
        });
    }

    /** Soft delete (@SQLDelete). Tahlillar tarixi qoladi; bepul kvota qaytmaydi (DONE hisoblanadi). */
    @Transactional
    public Optional<String> delete(long telegramUserId, long fieldId) {
        return owned(telegramUserId, fieldId).map(f -> {
            fieldRepository.delete(f);
            auditService.event(AuditEvent.FIELD_DELETED).target("FIELD", fieldId)
                    .detail("telegramUserId", telegramUserId).record();
            return f.getName();
        });
    }

    private Optional<FieldEntity> owned(long telegramUserId, long fieldId) {
        return fieldRepository.findById(fieldId).filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId);
    }

    private static FieldCard card(FieldEntity f, FieldRepository.LastJobs j) {
        return new FieldCard(f.getId(), f.getName(), f.getCrop(), f.getAreaHa(),
                j == null ? null : j.getLastJobId(), j == null ? null : j.getLastStatus(),
                j == null ? null : j.getLastAt(), j == null ? null : j.getLastDoneJobId());
    }
}
