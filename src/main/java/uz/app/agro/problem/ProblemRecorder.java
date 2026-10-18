package uz.app.agro.problem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Xizmatlar xatoni shu yerga beradi → admin "Muammolar". HECH QACHON exception tashlamaydi: muammoni yozib bo'lmasa
 * (DB o'chiq) — faqat log, asosiy ish buzilmasin. Bir xil xato (raqamlari boshqa bo'lsa ham) bitta qatorga yig'iladi.
 * <pre>
 *   problems.error(ProblemSource.ANALYSIS, "Tahlil bajarilmadi: " + err).field(fieldId).ref("ANALYSIS", jobId).record();
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemRecorder {

    private static final int MESSAGE_MAX = 500;
    private static final int DETAIL_MAX = 4000;

    private final ProblemWriter writer;

    public Builder error(ProblemSource source, String message) {
        return new Builder(source, ProblemEntity.Level.ERROR, message);
    }

    /** Fermer tomonidagi sabab (dala juda kichik, botni bloklagan) — tizim xatosi emas, lekin ko'rinsin */
    public Builder warn(ProblemSource source, String message) {
        return new Builder(source, ProblemEntity.Level.WARN, message);
    }

    public final class Builder {
        private final ProblemSource source;
        private final ProblemEntity.Level level;
        private final String message;
        private Long farmerId, telegramUserId, fieldId, refId;
        private String refType;
        private Throwable cause;

        private Builder(ProblemSource source, ProblemEntity.Level level, String message) {
            this.source = source;
            this.level = level;
            this.message = message == null || message.isBlank() ? "(xabarsiz xato)" : message;
        }

        public Builder farmer(Long id) { this.farmerId = id; return this; }
        public Builder telegramUser(Long id) { this.telegramUserId = id; return this; }
        public Builder field(Long id) { this.fieldId = id; return this; }
        public Builder ref(String type, Long id) { this.refType = type; this.refId = id; return this; }
        public Builder cause(Throwable t) { this.cause = t; return this; }

        public void record() {
            try {
                String msg = cut(message.strip(), MESSAGE_MAX);
                writer.write(source, level, farmerId, telegramUserId, fieldId, refType, refId,
                        fingerprint(source, msg), msg, cause == null ? null : cut(stack(cause), DETAIL_MAX));
            } catch (RuntimeException e) {
                // Matnda fermer kiritmasi bo'lishi mumkin — log forging (CWE-117) bo'lmasin
                log.warn("muammo yozilmadi ({} {}): {} — sababi: {}", source, level,
                        message.replaceAll("[\\r\\n\\t]", " "), e.toString());
            }
        }
    }

    /** Raqamlar (id, vaqt, foiz) va UUID'lar olib tashlanadi — "job 12" va "job 13" bitta muammo */
    static String fingerprint(ProblemSource source, String message) {
        String normalized = message.toLowerCase().replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "#").replaceAll("\\d+", "#");
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest((source + "|" + normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stack(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
