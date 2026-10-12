package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.field.dto.FieldResponse;
import uz.app.agro.field.dto.FieldSummary;
import uz.app.agro.rbac.Permissions;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldService {

    private final FieldRepository fieldRepository;
    private final FieldMapper fieldMapper;

    @PreAuthorize(Permissions.CAN_READ_FIELD)
    public PageResponse<FieldSummary> search(Long farmerId, Crop crop, String country, Pageable pageable) {
        return PageResponse.of(fieldRepository
                .findAll(FieldSpecs.filter(farmerId, crop, country), pageable)
                .map(fieldMapper::toSummary));
    }

    @PreAuthorize(Permissions.CAN_READ_FIELD)
    public FieldResponse get(Long id) {
        return fieldRepository.findById(id)
                .map(fieldMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Dala", id));
    }
}
