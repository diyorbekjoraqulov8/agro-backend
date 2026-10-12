package uz.app.agro.farmer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.PlanService;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.dto.FarmerDetail;
import uz.app.agro.farmer.dto.FarmerListItem;
import uz.app.agro.farmer.dto.LeadUpdateRequest;
import uz.app.agro.field.Crop;
import uz.app.agro.field.FieldMapper;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.rbac.Permissions;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmerService {

    private final FarmerRepository farmerRepository;
    private final FieldRepository fieldRepository;
    private final FarmerMapper farmerMapper;
    private final FieldMapper fieldMapper;
    private final AuditService auditService;
    private final PlanService planService;

    @PreAuthorize(Permissions.CAN_READ_FARMER)
    public PageResponse<FarmerListItem> search(String country, LeadStatus leadStatus, String source,
                                               Crop crop, String q, Pageable pageable) {
        return PageResponse.of(farmerRepository
                .findAll(FarmerSpecs.filter(country, leadStatus, source, crop, q), pageable)
                .map(farmerMapper::toListItem));
    }

    @PreAuthorize(Permissions.CAN_READ_FARMER)
    public FarmerDetail get(Long id) {
        FarmerEntity farmer = find(id);
        return farmerMapper.toDetail(farmer,
                fieldMapper.toSummaryList(fieldRepository.findByFarmerIdOrderByIdDesc(id)),
                planService.effective(id, LocalDate.now()));
    }

    @PreAuthorize(Permissions.CAN_UPDATE_FARMER)
    @Transactional
    public FarmerDetail updateLead(Long id, LeadUpdateRequest req) {
        FarmerEntity farmer = find(id);
        LeadStatus old = farmer.getLeadStatus();
        farmer.setLeadStatus(req.leadStatus());
        farmer.setLeadNote(req.leadNote());
        auditService.event(AuditEvent.LEAD_STATUS_CHANGED)
                .target("FARMER", id)
                .detail("from", old.name())
                .detail("to", req.leadStatus().name())
                .record();
        return get(id);
    }

    private FarmerEntity find(Long id) {
        return farmerRepository.findById(id).orElseThrow(() -> new NotFoundException("Fermer", id));
    }
}
