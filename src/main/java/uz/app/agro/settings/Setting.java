package uz.app.agro.settings;

/**
 * Admin boshqaradigan sozlamalar. Kalit, tur, default va chegara — KODDA (bitta joy); DB'da (app_settings)
 * faqat admin o'zgartirgan qiymat. Yangi xizmat sozlamasi — shu yerga qator qo'shiladi, migratsiya shart emas.
 * group — admin panelda bo'lim nomi.
 */
public enum Setting {

    REFERRAL_ENABLED("referral.enabled", Type.BOOL, "true", null, null,
            "Takliflar", "Do'stlarni taklif qilish dasturi yoqilgan"),
    REFERRAL_REQUIRED("referral.required", Type.INT, "3", 1, 50,
            "Takliflar", "Nechta faol do'st uchun mukofot beriladi (faol — birinchi tahlili tayyor bo'lgan)"),
    REFERRAL_REWARD_DAYS("referral.reward-days", Type.INT, "30", 1, 365,
            "Takliflar", "Mukofot: necha kun bepul foydalanish"),
    REFERRAL_REWARD_PLAN("referral.reward-plan", Type.PLAN, "MONTHLY", null, null,
            "Takliflar", "Mukofot qaysi tarif bo'yicha beriladi (tarif kodi, pullik va faol)"),

    NOTIFICATION_RATE("notification.rate-per-second", Type.INT, "20", 1, 30,
            "Bildirishnomalar", "Sekundiga nechta xabar yuboriladi (Telegram limiti ~30/s; kampaniya ham shu tezlikda)"),
    NOTIFICATION_MAX_ATTEMPTS("notification.max-attempts", Type.INT, "4", 1, 10,
            "Bildirishnomalar", "Yetkazib bo'lmasa necha marta urinadi (oraliq: 1, 5, 30 daqiqa, keyin har soat)");

    public enum Type { BOOL, INT, STRING, PLAN }

    private final String key;
    private final Type type;
    private final String defaultValue;
    private final Integer min;
    private final Integer max;
    private final String group;
    private final String description;

    Setting(String key, Type type, String defaultValue, Integer min, Integer max, String group, String description) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.group = group;
        this.description = description;
    }

    public String key() { return key; }
    public Type type() { return type; }
    public String defaultValue() { return defaultValue; }
    public Integer min() { return min; }
    public Integer max() { return max; }
    public String group() { return group; }
    public String description() { return description; }

    public static Setting byKey(String key) {
        for (Setting s : values()) if (s.key.equals(key)) return s;
        return null;
    }
}
