package uz.app.agro.advisor;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.advisor.dto.AdvisorItem;
import uz.app.agro.advisor.dto.AdvisorStats;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.rbac.Permissions;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin "AI maslahat": tarix va statistika (agronom tekshiruvi yo'q — AI tavsiyasi fermerga yakuniy boradi) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvisorAdminService {

    public enum Filter { ALL, PENDING, ANSWERED, FAILED }

    private final AdvisorRequestRepository requests;
    private final FarmerRepository farmers;
    private final FieldRepository fields;

    @PreAuthorize(Permissions.CAN_READ_ADVISOR)
    public PageResponse<AdvisorItem> list(Filter filter, Long farmerId, Pageable pageable) {
        Specification<AdvisorRequestEntity> spec = (root, q, cb) -> {
            var ps = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (filter != Filter.ALL) ps.add(cb.equal(root.get("status"), AdvisorRequestEntity.Status.valueOf(filter.name())));
            if (farmerId != null) ps.add(cb.equal(root.get("farmerId"), farmerId));
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AdvisorRequestEntity> page = requests.findAll(spec, pageable);
        // Ism va dala nomlari — sahifa uchun ikki so'rov (N+1 emas)
        Map<Long, FarmerEntity> fs = byId(farmers.findAllById(ids(page, AdvisorRequestEntity::getFarmerId)), FarmerEntity::getId);
        Map<Long, FieldEntity> fl = byId(fields.findAllById(ids(page, AdvisorRequestEntity::getFieldId)), FieldEntity::getId);
        return PageResponse.of(page.map(r -> toItem(r, fs.get(r.getFarmerId()), r.getFieldId() == null ? null : fl.get(r.getFieldId()))));
    }

    @PreAuthorize(Permissions.CAN_READ_ADVISOR)
    public AdvisorItem get(Long id) {
        AdvisorRequestEntity r = require(id);
        return toItem(r, farmers.findById(r.getFarmerId()).orElse(null),
                r.getFieldId() == null ? null : fields.findById(r.getFieldId()).orElse(null));
    }

    @PreAuthorize(Permissions.CAN_READ_ADVISOR)
    public AdvisorStats stats() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Map<String, Long> byStatus = requests.statusCounts(since).stream()
                .collect(Collectors.toMap(AdvisorRequestRepository.StatusCount::getKey, AdvisorRequestRepository.StatusCount::getCount));
        Map<String, Long> byCategory = requests.categoryCounts(since).stream()
                .collect(Collectors.toMap(AdvisorRequestRepository.StatusCount::getKey, AdvisorRequestRepository.StatusCount::getCount));
        return new AdvisorStats(byStatus, byCategory, requests.tokensByModel(since).stream()
                .map(t -> new AdvisorStats.ModelTokens(t.getModel(), t.getInputTokens(), t.getOutputTokens(), t.getRequests())).toList());
    }

    private AdvisorRequestEntity require(Long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("AI maslahat so'rovi", id));
    }

    private static Set<Long> ids(Page<AdvisorRequestEntity> page, Function<AdvisorRequestEntity, Long> f) {
        return page.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static AdvisorItem toItem(AdvisorRequestEntity r, FarmerEntity farmer, FieldEntity field) {
        String name = farmer == null ? null : farmer.getFullName() != null ? farmer.getFullName()
                : farmer.getTelegramUsername() != null ? "@" + farmer.getTelegramUsername() : null;
        return new AdvisorItem(r.getId(), r.getFarmerId(), name, r.getFieldId(), field == null ? null : field.getName(),
                r.getPhotoHashId(), r.getFarmerNote(), r.getLanguage(), r.getStatus(), r.getAttempts(), r.getAiModel(),
                r.getAiResult(), r.getAiError(), r.getInputTokens(), r.getOutputTokens(), r.getCreatedDate(), r.getAnsweredAt());
    }
}
