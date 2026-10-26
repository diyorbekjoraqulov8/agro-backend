package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisJob;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.analysis.AnalysisService;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQAT bot chaqiradi (Mini App → web_app_data). @PreAuthorize YO'Q — controller'ga ULANMASIN.
 * Dala + bepul tahlil job'i + audit — BITTA tranzaksiyada: yarmi saqlanib qolmasin.
 */
@Service
@RequiredArgsConstructor
public class FieldIntakeService {

    private final FarmerRepository farmerRepository;
    private final FieldRepository fieldRepository;
    private final AnalysisJobRepository jobRepository;
    private final AuditService auditService;
    private final PlanService planService;
    private final FieldSeasonService seasonService;

    /** AREA_LIMIT — bitta dala chegarasi; TOTAL_LIMIT — tarifdagi jami maydon (usedHa — boshqa dalalar) */
    public enum Outcome { QUEUED, QUOTA_USED, FIELD_LIMIT, AREA_LIMIT, TOTAL_LIMIT, NOT_FOUND, BUSY }

    /** areaHa — Java'dagi taxminiy (sferik) maydon; aniq maydonni tahlildan keyin Python beradi */
    /** askSowing — tahrirda ekin almashdi va mavsum yangilandi: bot ekish sanasini qayta so'raydi */
    public record Result(Outcome outcome, Long fieldId, Long jobId, EffectivePlan plan, double areaHa, double usedHa,
                         boolean askSowing) {
        public Result(Outcome outcome, Long fieldId, Long jobId, EffectivePlan plan, double areaHa, double usedHa) {
            this(outcome, fieldId, jobId, plan, areaHa, usedHa, false);
        }

        public Result(Outcome outcome, Long fieldId, Long jobId, EffectivePlan plan, double areaHa) {
            this(outcome, fieldId, jobId, plan, areaHa, 0);
        }

        Result askingSowing() {
            return new Result(outcome, fieldId, jobId, plan, areaHa, usedHa, true);
        }
    }

    private static final List<AnalysisJob.Status> ACTIVE = List.of(AnalysisJob.Status.PENDING, AnalysisJob.Status.RUNNING);

    /**
     * Mini App'dan kelgan poligon: yangi dala yoki (editFieldId != null) mavjud dala chegarasini tahrirlash.
     * @param ring yopiq halqa [[lon, lat], ...] — BotPolygonParser tekshirgan
     */
    @Transactional
    public Result submit(long telegramUserId, String name, List<double[]> ring, Long editFieldId, Crop crop) {
        FarmerEntity farmer = requireFarmer(telegramUserId);
        // Qulf: bir vaqtda yuborilgan ikki dala dalalar soni / jami maydon limitini birga oshirib yubormasin
        farmerRepository.findLockedById(farmer.getId());
        // Limitlar — fermerning BUGUNGI tarifidan (to'lov yo'q — FREE)
        EffectivePlan plan = planService.effective(farmer.getId(), LocalDate.now());
        double areaHa = areaHa(ring);

        FieldEntity field;
        if (editFieldId != null) {
            // callback/web_app_data ishonchsiz — dala SHU fermerniki ekanini tekshiramiz (IDOR)
            field = fieldRepository.findById(editFieldId)
                    .filter(f -> f.getFarmer().getId().equals(farmer.getId())).orElse(null);
            if (field == null) return new Result(Outcome.NOT_FOUND, null, null, plan, areaHa);
        } else {
            if (!plan.fieldsLeft(fieldRepository.countByFarmerId(farmer.getId()))) {
                return new Result(Outcome.FIELD_LIMIT, null, null, plan, areaHa);
            }
            field = new FieldEntity();
            field.setFarmer(farmer);
            field.setCrop(farmer.getMainCrop() != null ? farmer.getMainCrop() : Crop.OTHER);
            field.setIrrigationMethod(farmer.getIrrigationType());
        }
        // Mini App ham tekshiradi, lekin uni chetlab o'tish mumkin — qaror server tomonda
        if (areaHa > plan.maxFieldHa().doubleValue()) {
            return new Result(Outcome.AREA_LIMIT, field.getId(), null, plan, areaHa);
        }
        Result total = totalLimit(farmer, plan, field.getId(), areaHa);
        if (total != null) return total;
        if (editFieldId != null && jobRepository.existsByFieldIdAndStatusIn(field.getId(), ACTIVE)) {
            return new Result(Outcome.BUSY, field.getId(), null, plan, areaHa);   // eski chegara bilan tahlil ketyapti
        }

        // Ekin — har DALANIKI (bitta xo'jalikda g'alla, paxta, poliz): Mini App'da tanlangan. Tekshiruvlardan KEYIN —
        // rad etilgan tahrirning yarmi (managed entity) commit bo'lib qolmasin
        Crop oldCrop = field.getCrop();
        if (crop != null) {
            field.setCrop(crop);
        }
        field.setName(name);
        field.setBoundaryGeojson(geojson(ring));
        // Taxminiy maydon — jami limit hisobi uchun darhol; aniq maydonni keyingi tahlil yozadi (Python, UTM)
        field.setAreaHa(BigDecimal.valueOf(areaHa).setScale(2, RoundingMode.HALF_UP));
        // Taxminiy markaz (uchlar o'rtachasi) — ro'yxat/xarita uchun
        double lon = 0, lat = 0;
        int n = ring.size() - 1;                     // oxirgi nuqta = birinchi
        for (int i = 0; i < n; i++) { lon += ring.get(i)[0]; lat += ring.get(i)[1]; }
        field.setCentroidLon(BigDecimal.valueOf(lon / n).setScale(6, RoundingMode.HALF_UP));
        field.setCentroidLat(BigDecimal.valueOf(lat / n).setScale(6, RoundingMode.HALF_UP));
        boolean created = field.getId() == null;
        fieldRepository.save(field);
        if (created) seasonService.start(field.getId(), field.getCrop());   // ekish sanasini bot keyin so'raydi
        // Tahrirda Mini App ekinni ham almashtira oladi — mavsum FieldBotService.setCrop dagi qoida bilan
        boolean askSowing = !created && field.getCrop() != oldCrop && seasonService.cropChanged(field.getId(), field.getCrop());

        auditService.event(editFieldId != null ? AuditEvent.FIELD_UPDATED : AuditEvent.FIELD_CREATED)
                .target("FIELD", field.getId())
                .detail("farmerId", farmer.getId())
                .detail("vertices", n)
                .detail("areaHaApprox", Math.round(areaHa * 100) / 100.0)
                .detail("plan", plan.planCode())
                .record();

        Result r = queue(field, plan, areaHa);
        return askSowing ? r.askingSowing() : r;
    }

    /** "🔄 Qayta tahlil" — mavjud dala, kvota va "bitta faol tahlil" qoidasi bilan. */
    @Transactional
    public Result reanalyze(long telegramUserId, long fieldId) {
        FarmerEntity farmer = requireFarmer(telegramUserId);
        EffectivePlan plan = planService.effective(farmer.getId(), LocalDate.now());
        FieldEntity field = fieldRepository.findById(fieldId)
                .filter(f -> f.getFarmer().getId().equals(farmer.getId())).orElse(null);
        if (field == null) return new Result(Outcome.NOT_FOUND, null, null, plan, 0);
        double areaHa = field.getAreaHa() != null ? field.getAreaHa().doubleValue() : 0;
        if (jobRepository.existsByFieldIdAndStatusIn(fieldId, ACTIVE)) {
            return new Result(Outcome.BUSY, fieldId, null, plan, areaHa);
        }
        // Tarif pasaygan bo'lsa (pullik tugadi) — katta dala endi limitdan oshadi
        if (areaHa > plan.maxFieldHa().doubleValue()) {
            return new Result(Outcome.AREA_LIMIT, fieldId, null, plan, areaHa);
        }
        // Katta tarifdan tushgan (Agro Biznes → Pro): jami limitdan oshgan xo'jalik qayta tahlil qila olmaydi
        Result total = totalLimit(farmer, plan, fieldId, areaHa);
        if (total != null) return total;
        return queue(field, plan, areaHa);
    }

    /**
     * Jami maydon limiti (null — cheksiz). submit fermer qatorini qulflaydi — bir vaqtda yuborilgan ikki dala ikkalasi
     * ham "sig'adi" deb o'tib ketmasin; reanalyze maydon qo'shmaydi (qulf shart emas). @return oshsa — natija, aks holda null
     */
    private Result totalLimit(FarmerEntity farmer, EffectivePlan plan, Long fieldId, double areaHa) {
        if (plan.maxTotalHa() == null) return null;
        double used = fieldRepository.sumAreaHa(farmer.getId(), fieldId != null ? fieldId : -1L).doubleValue();
        // 0.01 — taxminiy maydon yaxlitlanadi (2 kasr); chegaradagi dala yaxlitlash tufayli rad etilmasin
        if (used + areaHa > plan.maxTotalHa().doubleValue() + 0.01) {
            return new Result(Outcome.TOTAL_LIMIT, fieldId, null, plan, areaHa, used);
        }
        return null;
    }

    // Tahlil kvotasi: FREE — umrbod, pullik — obuna davrida (FAILED hisoblanmaydi)
    private Result queue(FieldEntity field, EffectivePlan plan, double areaHa) {
        if (!plan.analysesLeft()) {
            return new Result(Outcome.QUOTA_USED, field.getId(), null, plan, areaHa);
        }
        AnalysisService.Window w = AnalysisService.defaultWindow(LocalDate.now());
        AnalysisJob job = new AnalysisJob();
        job.setField(field);
        job.setKind(plan.paid() ? AnalysisJob.Kind.PAID : AnalysisJob.Kind.FREE);
        job.setCrop(field.getCrop());
        job.setSeasonStart(w.start());
        job.setSeasonEnd(w.end());
        jobRepository.save(job);

        auditService.event(AuditEvent.ANALYSIS_REQUESTED)
                .target("ANALYSIS", job.getId())
                .detail("fieldId", field.getId())
                .detail("kind", job.getKind().name())
                .record();
        return new Result(Outcome.QUEUED, field.getId(), job.getId(), plan, areaHa);
    }

    private FarmerEntity requireFarmer(long telegramUserId) {
        return farmerRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("fermer yo'q: tg " + telegramUserId));
    }

    /**
     * Sferik maydon (turf.area / Mini App bilan bir xil algoritm, WGS84 radiusi), gektar.
     * pyproj geodezik qiymatidan ~0,1% farq — limit tekshiruvi uchun yetarli.
     */
    static double areaHa(List<double[]> ring) {
        final double r = 6378137;
        int n = ring.size() - 1;                     // oxirgi = birinchi
        if (n < 3) return 0;
        double s = 0;
        for (int i = 0; i < n; i++) {
            double[] p1 = ring.get(i), p2 = ring.get((i + 1) % n), p3 = ring.get((i + 2) % n);
            s += (Math.toRadians(p3[0]) - Math.toRadians(p1[0])) * Math.sin(Math.toRadians(p2[1]));
        }
        return Math.abs(s * r * r / 2) / 10_000;
    }

    private static Map<String, Object> geojson(List<double[]> ring) {
        Map<String, Object> g = new HashMap<>();
        g.put("type", "Polygon");
        g.put("coordinates", List.of(ring.stream().map(p -> List.of(p[0], p[1])).toList()));
        return g;
    }
}
