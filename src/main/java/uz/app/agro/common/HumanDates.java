package uz.app.agro.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fermer uchun sana: "11-oktabr" / "11 октября" / "11 October" ("11.10" — tushunarsiz). Boshqa yil — yili bilan.
 * Ruscha/inglizcha — JDK CLDR (ruscha qaratqich kelishigida: "октября"); o'zbekcha — o'zimiz (CLDR lotin/kirill aralash).
 * Bot (BotMessages.date) va bildirishnomalar (NotificationService — LocalDate argumentlari) shu yerdan.
 */
public final class HumanDates {

    private static final String[] UZ_MONTHS = {"yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust",
            "sentyabr", "oktabr", "noyabr", "dekabr"};

    private HumanDates() {}

    public static String format(String lang, LocalDate d) {
        if (d == null) return "—";
        boolean otherYear = d.getYear() != LocalDate.now().getYear();
        return switch (lang) {
            // Boshqa yil: "2027-yil 15-noyabr" (shablon qo'shimchasi oxiriga: "...15-noyabrgacha")
            case "uz" -> (otherYear ? d.getYear() + "-yil " : "") + d.getDayOfMonth() + "-" + UZ_MONTHS[d.getMonthValue() - 1];
            case "ru" -> d.format(DateTimeFormatter.ofPattern(otherYear ? "d MMMM yyyy" : "d MMMM", Locale.forLanguageTag("ru")));
            default -> d.format(DateTimeFormatter.ofPattern(otherYear ? "d MMMM yyyy" : "d MMMM", Locale.ENGLISH));
        };
    }
}
