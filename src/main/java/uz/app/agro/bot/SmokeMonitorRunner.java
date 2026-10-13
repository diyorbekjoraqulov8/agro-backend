package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.monitoring.MonitoringBotService;
import uz.app.agro.notification.NotificationChannel.Recipient;

import java.util.HashMap;
import java.util.Map;

/** VAQTINCHALIK smoke runner — sinovdan keyin O'CHIRILADI */
@Component
@ConditionalOnProperty(name = "smoke.monitor")
class SmokeMonitorRunner implements ApplicationRunner {

    private final BotMessages messages;
    private final FileRegistry files;
    private final MonitoringBotService monitoring;
    private final JdbcTemplate jdbc;

    SmokeMonitorRunner(BotMessages messages, FileRegistry files, MonitoringBotService monitoring, JdbcTemplate jdbc) {
        this.messages = messages;
        this.files = files;
        this.monitoring = monitoring;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        BotSender fake = new BotSender(null, null) {
            @Override
            public Integer send(long chatId, String text, Keyboard keyboard) {
                System.out.println("SMOKE SEND chat=" + chatId + "\n" + text + "\nKB=" + kb(keyboard) + "\nSMOKE END");
                return 1;
            }

            @Override
            public boolean sendPhoto(long chatId, byte[] png, String caption, Keyboard keyboard) {
                System.out.println("SMOKE PHOTO chat=" + chatId + " bytes=" + png.length + " captionLen=" + caption.length()
                        + "\n" + caption + "\nKB=" + kb(keyboard) + "\nSMOKE END");
                return true;
            }
        };
        BotMonitoring bm = new BotMonitoring(fake, messages, files, monitoring);

        for (String lang : new String[]{"uz", "ru", "en"}) {
            Map<String, Object> p = new HashMap<>();
            p.put("fieldId", 6);
            p.put("fieldName", "Beshqo'rg'on");
            p.put("sceneDate", "2026-10-10");
            p.put("refDate", "2026-09-25");
            p.put("declinePct", 62.5);
            p.put("side", "NE");
            p.put("fileHashId", "tMrocqJuJkygAdzygirLAg");
            System.out.println("SMOKE alert " + lang + " with map -> " + bm.alert(new Recipient(1, 990000001, 990000001, lang, false), p));
            p.remove("fileHashId");
            p.put("side", null);
            p.put("declinePct", 1234.4);
            System.out.println("SMOKE alert " + lang + " no map, side null, 1234.4 -> " + bm.alert(new Recipient(1, 990000001, 990000001, lang, false), p));
        }
        Map<String, Object> bad = new HashMap<>();
        bad.put("fieldId", 1L); bad.put("fieldName", "X"); bad.put("sceneDate", "2026-10-10"); bad.put("refDate", "2026-09-25");
        bad.put("declinePct", 15); bad.put("side", "S"); bad.put("fileHashId", "doesNotExist00000000000");
        System.out.println("SMOKE alert bad hash -> " + bm.alert(new Recipient(1, 990000001, 990000001, "uz", false), bad));

        set("monitoring.enabled", "true");
        set("monitoring.paid-only", "true");
        for (String lang : new String[]{"uz", "ru", "en"}) {
            line(bm, lang, "paid f1 (DECLINE)", 990000001L, 1);
            line(bm, lang, "paid f4 (NO_CROP)", 990000001L, 4);
            line(bm, lang, "paid f7 (no events)", 990000001L, 7);
            line(bm, lang, "free f6 paid-only=true", 990000002L, 6);
        }
        line(bm, "uz", "foreign field (f6 by farmer1)", 990000001L, 6);
        line(bm, "uz", "unknown field 9999", 990000001L, 9999);
        set("monitoring.paid-only", "false");
        line(bm, "uz", "free f6 paid-only=false", 990000002L, 6);
        set("monitoring.enabled", "false");
        line(bm, "uz", "paid f1 enabled=false", 990000001L, 1);
        set("monitoring.enabled", "true");
        set("monitoring.paid-only", "false");
        System.out.println("SMOKE DONE");
    }

    private void line(BotMonitoring bm, String lang, String label, long tg, long fieldId) {
        System.out.println("SMOKE card [" + lang + "] " + label + " -> [" + bm.cardLine(lang, tg, fieldId) + "]");
    }

    private void set(String key, String value) {
        jdbc.update("INSERT INTO app_settings(key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value", key, value);
    }

    private static String kb(Keyboard k) {
        if (!(k instanceof InlineKeyboardMarkup m)) return String.valueOf(k);
        StringBuilder sb = new StringBuilder();
        for (var row : m.inlineKeyboard()) for (var b : row) sb.append("[").append(b.text()).append(" | ").append(b.callbackData()).append("] ");
        return sb.toString();
    }
}
