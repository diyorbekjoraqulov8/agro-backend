package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.satellite.dto.AnalyzeResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Saqlangan hisobotni botga qayta berish (📊 Hisobot, 📄 PDF, 📊 Excel).
 * FAQAT bot — @PreAuthorize yo'q; egalik: tahlil SHU Telegram foydalanuvchisining dalasiga tegishli.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisReportService {

    private final AnalysisJobRepository jobRepository;
    private final AnalysisImageRepository imageRepository;
    private final AnalysisImages images;

    public record StoredReport(JobInput input, AnalyzeResponse result) {}

    public Optional<StoredReport> forFarmer(long telegramUserId, long jobId) {
        return jobRepository.findById(jobId)
                .filter(j -> j.getStatus() == AnalysisJob.Status.DONE && j.getResult() != null)
                .flatMap(j -> {
                    FieldEntity f = fieldOf(j);
                    if (f == null || f.getFarmer().getTelegramUserId() != telegramUserId) return Optional.empty();
                    return imageRepository.findById(jobId).map(img -> {
                        // result JSON (snake_case) + rasmlar → AnalyzeResponse (bitta hisobot kodi)
                        Map<String, Object> m = new HashMap<>(j.getResult());
                        m.put("map_png_base64", Base64.getEncoder().encodeToString(images.map(img)));
                        m.put("chart_png_base64", Base64.getEncoder().encodeToString(images.chart(img)));
                        AnalyzeResponse r = JsonMapper.shared().convertValue(m, AnalyzeResponse.class);
                        var farmer = f.getFarmer();
                        JobInput in = new JobInput(j.getId(), j.getKind(), f.getId(), f.getName(), Map.of(),
                                j.getCrop() != null ? j.getCrop() : f.getCrop(),
                                j.getSeasonStart(), j.getSeasonEnd(), farmer.getId(), farmer.getTelegramUserId(),
                                farmer.getChatId(), farmer.getLanguage(), farmer.isBlockedBot());
                        return new StoredReport(in, r);
                    });
                });
    }

    // Dala o'chirilgan — @SQLRestriction uni "yo'q" qiladi
    private static FieldEntity fieldOf(AnalysisJob j) {
        try {
            FieldEntity f = j.getField();
            return f != null && f.getName() != null ? f : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }
}
