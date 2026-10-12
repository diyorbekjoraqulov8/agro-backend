package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.analysis.JobInput;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.dto.AnalyzeResponse;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Hisobot → Telegram: 2 rasm + xulosa (ostida 📄 PDF / 📊 Excel tugmalari).
 * Yangi tahlildan keyin (worker) ham, "📊 Hisobot" bosilganda (saqlangan natijadan) ham shu kod.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotReportSender {

    private static final long PAUSE_MS = 1100;          // Telegram: bitta chatga ~1 xabar/s
    private static final Pattern UNSAFE_FILE = Pattern.compile("[^\\p{L}\\p{N}_-]+");

    private final BotSender sender;
    private final BotReportFormatter format;
    private final BotKeyboards keyboards;
    private final PlanService plans;
    private final SatelliteClient satellite;

    /** @return false — birinchi rasm yuborilmadi (blok, tarmoq) */
    public boolean send(long chatId, String lang, JobInput in, AnalyzeResponse r) {
        if (!sender.sendPhoto(chatId, r.mapPng(), format.mapCaption(lang, in.fieldName(), in.crop(), r), null)) {
            return false;
        }
        pause();
        sender.sendPhoto(chatId, r.chartPng(), format.chartCaption(lang), null);
        pause();
        sender.send(chatId, format.summary(lang, r, plan(in)), keyboards.reportFiles(lang, in.jobId()));
        return true;
    }

    /** PDF — Python'da (DejaVu shrifti, kirill/ʻ); xato bo'lsa fermerga qisqa xabar */
    public void sendPdf(long chatId, String lang, JobInput in, AnalyzeResponse r) {
        try {
            byte[] pdf = satellite.reportPdf(format.pdf(lang, in.fieldName(), in.crop(), in.seasonEnd(), r, plan(in)));
            sender.sendDocument(chatId, pdf, fileName(in, "pdf"), "application/pdf",
                    format.fileCaption(lang, "file.pdfCaption", in.fieldName()));
        } catch (RuntimeException e) {
            log.warn("job {}: PDF tayyorlanmadi: {}", in.jobId(), e.getMessage());
            sender.send(chatId, format.fileFailed(lang), null);
        }
    }

    public void sendCsv(long chatId, String lang, JobInput in, AnalyzeResponse r) {
        sender.sendDocument(chatId, format.csv(lang, in.fieldName(), in.crop(), r), fileName(in, "csv"),
                "text/csv", format.fileCaption(lang, "file.csvCaption", in.fieldName()));
    }

    private EffectivePlan plan(JobInput in) {
        return plans.effective(in.farmerId(), LocalDate.now());
    }

    // "agro-Mening_dalam-2026-10-11.pdf" — fayl nomida faqat harf/raqam
    private static String fileName(JobInput in, String ext) {
        String name = UNSAFE_FILE.matcher(in.fieldName()).replaceAll("_");
        if (name.length() > 40) name = name.substring(0, 40);
        return "agro-" + name + "-" + in.seasonEnd() + "." + ext;
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
