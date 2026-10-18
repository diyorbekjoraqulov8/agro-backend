package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import uz.app.agro.common.HumanDates;

import java.util.Locale;
import java.util.Set;

/**
 * Matnlar: resources/bot/messages*.properties (en — asosiy, uz, ru).
 * ⚠️ O'zbekcha tutuq belgisi: ' EMAS, ʻ (U+02BB) va ’ (U+2019). Parametrli xabarda MessageFormat
 * oddiy ' ni maxsus belgi deb yutib yuboradi ("o'zingiz" → "ozingiz").
 */
@Component
@RequiredArgsConstructor
public class BotMessages {

    public static final Set<String> LANGUAGES = Set.of("uz", "ru", "en");

    private final MessageSource messageSource;

    /** Telegram language_code → qo'llab-quvvatlanadigan til. Noma'lum — en. */
    public static String resolveLanguage(String telegramCode) {
        if (telegramCode == null) return "en";
        String base = telegramCode.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return LANGUAGES.contains(base) ? base : "en";
    }

    public String text(String lang, String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.forLanguageTag(lang));
    }

    /** "11-oktabr" / "11 октября" / "11 October" — HumanDates */
    public static String date(String lang, java.time.LocalDate d) {
        return HumanDates.format(lang, d);
    }

    public String label(String lang, Enum<?> value) {
        return text(lang, value.getDeclaringClass().getSimpleName() + "." + value.name());
    }
}
