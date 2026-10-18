package uz.app.agro.problem;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tranzaksiya chegarasi alohida bean'da (CLAUDE.md 5.4, ADR #24): REQUIRES_NEW — chaqiruvchi rollback bo'lsa ham
 * muammo yozuvi qoladi (xato aynan rollback'ga olib kelgan bo'lishi mumkin).
 */
@Component
@RequiredArgsConstructor
class ProblemWriter {

    private final ProblemRepository repository;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ProblemSource source, ProblemEntity.Level level, Long farmerId, Long telegramUserId, Long fieldId,
                      String refType, Long refId, String fingerprint, String message, String detail) {
        Long farmer = farmerId;
        // Soft-delete qilingan dala/fermer ham topilsin — @SQLRestriction'siz to'g'ridan-to'g'ri SQL
        if (farmer == null && fieldId != null) farmer = first(jdbc.queryForList("SELECT farmer_id FROM fields WHERE id = ?", Long.class, fieldId));
        if (farmer == null && telegramUserId != null) {
            farmer = first(jdbc.queryForList("SELECT id FROM farmers WHERE telegram_user_id = ?", Long.class, telegramUserId));
        }
        repository.upsert(source.name(), level.name(), farmer, farmer == null ? 0 : farmer, fieldId, refType, refId,
                fingerprint, message, detail);
    }

    private static Long first(List<Long> list) {
        return list.isEmpty() ? null : list.getFirst();
    }
}
