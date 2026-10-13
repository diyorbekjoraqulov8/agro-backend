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
            "Bildirishnomalar", "Yetkazib bo'lmasa necha marta urinadi (oraliq: 1, 5, 30 daqiqa, keyin har soat)"),

    MONITORING_ENABLED("monitoring.enabled", Type.BOOL, "true", null, null,
            "Monitoring", "Avtomatik monitoring: har yangi sun'iy yo'ldosh suratida dala o'zi tekshiriladi"),
    MONITORING_PAID_ONLY("monitoring.paid-only", Type.BOOL, "true", null, null,
            "Monitoring", "Faqat pullik tarifdagi fermerlar dalalari (o'chirilsa — hamma; Python yuki oshadi)"),
    MONITORING_DROP("monitoring.drop", Type.INT, "10", 5, 30,
            "Monitoring", "Piksel \"sustlashdi\": NDVI shuncha yuzdan bir birlikka kamaydi (10 → ΔNDVI ≤ −0.10)"),
    MONITORING_DECLINE_PCT("monitoring.decline-pct", Type.INT, "15", 5, 80,
            "Monitoring", "Dalaning necha foizi sustlashsa fermerga xabar boradi"),

    // Xavf darajasi: chegaraga yetsa — WARNING, quyidagi qadam bilan — DANGER (WeatherRisk)
    WEATHER_ALERTS_ENABLED("weather.alerts-enabled", Type.BOOL, "true", null, null,
            "Ob-havo", "Ob-havo xavfi ogohlantirishlari yoqilgan (prognoz botda baribir ko'rinadi)"),
    WEATHER_FROST_C("weather.frost-c", Type.INT, "0", -10, 5,
            "Ob-havo", "Sovuq: minimal harorat shundan past yoki teng (°C). Faqat mart–may va sentyabr–noyabr; 3° pastroq — xavfli"),
    WEATHER_HEAT_C("weather.heat-c", Type.INT, "40", 30, 50,
            "Ob-havo", "Issiq: maksimal harorat shundan yuqori yoki teng (°C); 3° yuqori — xavfli. 5 kunda ko'pi bilan bir marta"),
    WEATHER_WIND_MS("weather.wind-ms", Type.INT, "15", 8, 40,
            "Ob-havo", "Kuchli shamol: shamol zarbi (gust) shundan kuchli yoki teng (m/s); +5 m/s — xavfli"),
    WEATHER_RAIN_MM("weather.rain-mm", Type.INT, "20", 5, 200,
            "Ob-havo", "Jala: sutkalik yog'in shundan ko'p yoki teng (mm); 2 barobar — xavfli. Do'l/momaqaldiroq — har doim");

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
