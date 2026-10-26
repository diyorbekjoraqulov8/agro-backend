package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisService;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.field.dto.CreateLabFieldRequest;
import uz.app.agro.field.dto.FieldResponse;
import uz.app.agro.rbac.Permissions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * L1: Lab dalalari — admin xaritada chizadi, egasi Lab fermer (V41). Yaratilgach dala fermer dalasi bilan BIR XIL
 * yo'ldan o'tadi: mavsum, birinchi tahlil, monitoring, ob-havo. Xabarlar Telegram'ga ketmaydi (NotificationDispatcher).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabFieldService {

    private final FarmerRepository farmerRepository;
    private final FieldRepository fieldRepository;
    private final FieldSeasonService seasonService;
    private final FieldSeasonRepository seasonRepository;
    private final FieldMapper fieldMapper;
    private final AnalysisService analysisService;
    private final PlanService planService;
    private final AuditService auditService;

    /** Birinchi tahlil (STAFF) navbatga qo'yiladi */
    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public FieldResponse create(CreateLabFieldRequest req) {
        FarmerEntity lab = farmerRepository.findByLabTrue()
                .orElseThrow(() -> new IllegalStateException("Lab fermer yo'q (V41 migratsiyasi)"));
        List<double[]> ring = ring(req.coordinates());
        LocalDate today = LocalDate.now();
        if (req.sownOn() != null && !FieldSeasonService.validSownOn(req.sownOn(), today)) {
            throw new BadRequestException("Ekish sanasi kelajakda yoki " + FieldSeasonService.MAX_AGE_DAYS + " kundan eski");
        }
        EffectivePlan plan = planService.effective(lab.getId(), today);
        double areaHa = FieldIntakeService.areaHa(ring);
        if (areaHa > plan.maxFieldHa().doubleValue()) {
            throw new BadRequestException("Dala " + Math.round(areaHa) + " ga — chegara " + plan.maxFieldHa() + " ga");
        }

        FieldEntity field = new FieldEntity();
        field.setFarmer(lab);
        field.setName(Rings.name(req.name(), "Lab dala"));
        field.setCrop(req.crop());
        field.setIrrigationMethod(req.irrigationMethod());
        FieldIntakeService.applyBoundary(field, ring, areaHa);
        fieldRepository.save(field);
        seasonService.start(field.getId(), field.getCrop(), req.sownOn());

        auditService.event(AuditEvent.FIELD_CREATED).target("FIELD", field.getId())
                .detail("lab", true)
                .detail("vertices", ring.size() - 1)
                .detail("areaHaApprox", Math.round(areaHa * 100) / 100.0)
                .record();
        // Birinchi tahlil — xodim tahlili (STAFF) yo'li: analysis:run huquqi ham talab qilinadi (yo'q bo'lsa — 403, dala rollback)
        analysisService.run(field.getId(), null);
        return fieldMapper.toResponse(field, seasonRepository.findByFieldIdAndStatus(field.getId(), FieldSeasonEntity.Status.ACTIVE).orElse(null));
    }

    /** Faqat Lab dalasi — fermer dalasini admin o'chirmaydi. Soft delete: tahlillar tarixi qoladi */
    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public void delete(Long id) {
        FieldEntity f = fieldRepository.findById(id).orElseThrow(() -> new NotFoundException("Dala", id));
        if (!f.getFarmer().isLab()) throw new ConflictException("Faqat Lab dalasini o'chirish mumkin");
        fieldRepository.delete(f);
        auditService.event(AuditEvent.FIELD_DELETED).target("FIELD", id).detail("lab", true).record();
    }

    private static List<double[]> ring(List<List<Double>> coords) {
        List<double[]> ring = new ArrayList<>(coords.size() + 1);
        for (List<Double> p : coords) {
            if (!Rings.validPoint(p.get(0), p.get(1))) throw new BadRequestException("Koordinata noto'g'ri: " + p);
            ring.add(new double[]{p.get(0), p.get(1)});
        }
        if (!Rings.close(ring)) throw new BadRequestException("Dala 3 tadan " + Rings.MAX_VERTICES + " tagacha nuqtadan iborat bo'ladi");
        return ring;
    }
}
