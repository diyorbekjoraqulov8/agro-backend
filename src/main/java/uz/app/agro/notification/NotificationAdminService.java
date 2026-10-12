package uz.app.agro.notification;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.notification.dto.NotificationResponse;
import uz.app.agro.rbac.Permissions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin → Bildirishnomalar → Jurnal: barcha xabarlar, holat statistikasi, qayta yuborish */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationAdminService {

    private final NotificationRepository repository;
    private final FarmerRepository farmerRepository;
    private final AuditService auditService;

    @PreAuthorize(Permissions.CAN_READ_NOTIFICATION)
    public PageResponse<NotificationResponse> search(NotificationStatus status, NotificationCategory category, Long farmerId,
                                                     Long campaignId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<NotificationEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (category != null) p.add(cb.equal(root.get("category"), category));
            if (farmerId != null) p.add(cb.equal(root.get("farmerId"), farmerId));
            if (campaignId != null) p.add(cb.equal(root.get("campaignId"), campaignId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdDate"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.get("createdDate"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<NotificationEntity> page = repository.findAll(spec, pageable);
        // Fermer ismlari — bitta so'rovda (N+1 emas)
        Map<Long, FarmerEntity> farmers = farmerRepository.findAllById(page.getContent().stream().map(NotificationEntity::getFarmerId).distinct().toList())
                .stream().collect(Collectors.toMap(FarmerEntity::getId, Function.identity()));
        return PageResponse.of(page.map(n -> toResponse(n, farmers.get(n.getFarmerId()))));
    }

    /** Oxirgi 24 soat: holatlar bo'yicha; queue — hozir navbatda kutayotganlar */
    @PreAuthorize(Permissions.CAN_READ_NOTIFICATION)
    public Map<String, Long> stats() {
        return repository.countsSince(LocalDateTime.now().minusDays(1)).stream()
                .collect(Collectors.toMap(NotificationRepository.StatusCount::getStatus, NotificationRepository.StatusCount::getCount));
    }

    /** FAILED/SKIPPED → qayta navbatga (masalan fermer botni qayta ochgandan keyin) */
    @PreAuthorize(Permissions.CAN_SEND_NOTIFICATION)
    @Transactional
    public NotificationResponse retry(Long id) {
        NotificationEntity n = repository.findById(id).orElseThrow(() -> new NotFoundException("Bildirishnoma", id));
        if (n.getStatus() != NotificationStatus.FAILED && n.getStatus() != NotificationStatus.SKIPPED) {
            throw new ConflictException("Faqat yetkazilmagan (FAILED/SKIPPED) xabarni qayta yuborish mumkin");
        }
        n.setStatus(NotificationStatus.PENDING);
        n.setAttempts(0);
        n.setNextAttemptAt(LocalDateTime.now());
        auditService.event(AuditEvent.NOTIFICATION_RETRIED).target("NOTIFICATION", id).record();
        return toResponse(n, farmerRepository.findById(n.getFarmerId()).orElse(null));
    }

    private static NotificationResponse toResponse(NotificationEntity n, FarmerEntity f) {
        String name = f == null ? null : f.getFullName() != null ? f.getFullName() : f.getTelegramUsername() != null ? "@" + f.getTelegramUsername() : null;
        return new NotificationResponse(n.getId(), n.getFarmerId(), name, n.getChannel(), n.getCategory(), n.getTemplate(), n.getPreview(),
                n.getStatus(), n.getAttempts(), n.getLastError(), n.getCampaignId(), n.getCreatedDate(), n.getNextAttemptAt(), n.getSentAt());
    }
}
