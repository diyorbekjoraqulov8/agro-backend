package uz.app.agro.settings;

/**
 * Admin boshqaradigan sozlamalar. Kalit, tur, default va chegara — KODDA (bitta joy); DB'da (app_settings)
 * faqat admin o'zgartirgan qiymat. Yangi xizmat sozlamasi — shu yerga qator qo'shiladi, migratsiya shart emas.
 * group — admin panelda bo'lim nomi.
 */
public enum Setting {

    // Bot asosiy menyusi: o'chirilgan bo'lim tugmasi menyuda ko'rinmaydi (eski klaviaturadan bosilsa — "hozircha o'chiq").
    // ⚙️ Sozlamalar doim bor (tarif, til, yordam, qo'llanma, do'stlar — hammasi uning ichida). Fermer telefonidagi klaviatura menyu biriktirilgan keyingi javobda yangilanadi
    MENU_NEW_FIELD("menu.new-field", Type.BOOL, "true", null, null,
            "Bot menyusi", "🛰 Dalani tekshirish — eng tepadagi uzun tugma (o'chirilsa ham dalasi yo'q fermerga ko'rinadi)"),
    MENU_MY_FIELDS("menu.my-fields", Type.BOOL, "true", null, null,
            "Bot menyusi", "📋 Dalalarim"),
    MENU_IRRIGATION("menu.irrigation", Type.BOOL, "true", null, null,
            "Bot menyusi", "💧 Sug'orish"),
    MENU_WEATHER("menu.weather", Type.BOOL, "true", null, null,
            "Bot menyusi", "🌦 Ob-havo (o'chirilsa ham xavf ogohlantirishlari \"Ob-havo\" bo'limidagi sozlama bo'yicha boradi)"),
    MENU_ADVISOR("menu.advisor", Type.BOOL, "false", null, null,
            "Bot menyusi", "🤖 AI maslahat tugmasi (xizmatning o'zi — \"AI maslahat\" bo'limida; ANTHROPIC_API_KEY ham kerak)"),

    REFERRAL_ENABLED("referral.enabled", Type.BOOL, "true", null, null,
            "Takliflar", "Do'stlarni taklif qilish dasturi yoqilgan"),
    REFERRAL_REQUIRED("referral.required", Type.INT, "3", 1, 50,
            "Takliflar", "Nechta faol do'st uchun mukofot beriladi (faol — birinchi tahlili tayyor bo'lgan)"),
    REFERRAL_REWARD_DAYS("referral.reward-days", Type.INT, "30", 1, 365,
            "Takliflar", "Mukofot: necha kun bepul foydalanish"),
    REFERRAL_REWARD_PLAN("referral.reward-plan", Type.PLAN, "MONTHLY", null, null,
            "Takliflar", "Mukofot qaysi tarif bo'yicha beriladi (tarif kodi, pullik va faol)"),

    TRIAL_ENABLED("trial.enabled", Type.BOOL, "false", null, null,
            "Pro sinovi", "Yangi fermer roʻyxatdan oʻtishni (savollarni) tugatganda Pro'ni bepul sinab koʻradi. Bir fermerga "
                    + "bir marta; yoqilishidan oldin savollarni tugatib boʻlganlarga berilmaydi"),
    TRIAL_DAYS("trial.days", Type.INT, "14", 3, 60,
            "Pro sinovi", "Sinov necha kun (tugashidan 2 kun oldin va tugaganda fermerga xabar boradi)"),
    TRIAL_PLAN("trial.plan", Type.PLAN, "MONTHLY", null, null,
            "Pro sinovi", "Qaysi tarif sinovga beriladi (tarif kodi, pullik va faol; \"narx kelishiladi\" emas). Xush kelibsiz "
                    + "matni \"avtomatik kuzatuv, cheksiz qayta tahlil\" deydi — tarif shunday boʻlsin"),

    // Mavsumiy obuna: sotuv davrida fermer mavsum oxirigacha bir to'lov bilan oladi (oylar × narx − foiz). Chegirmalar bilan qo'shilmaydi
    SEASON_ENABLED("season.enabled", Type.BOOL, "false", null, null,
            "Mavsumiy obuna", "Mavsumiy obuna taklif qilinadi (botdagi tariflar, guruhdagi tarif berish, admin «Toʻlov qildi»)"),
    SEASON_PLAN("season.plan", Type.PLAN, "MONTHLY", null, null,
            "Mavsumiy obuna", "Qaysi tarif mavsumga sotiladi (narxli pullik tarif)"),
    SEASON_DISCOUNT("season.discount-pct", Type.INT, "20", 0, 90,
            "Mavsumiy obuna", "Chegirma foizi (oylik narx × qolgan oylar dan)"),
    SEASON_SALE_FROM("season.sale-from", Type.DATE, "2027-03-01", null, null,
            "Mavsumiy obuna", "Sotuv qachondan boshlanadi (shu kundan mavsum oxirigacha taklif koʻrinadi)"),
    SEASON_END("season.end", Type.DATE, "2027-10-31", null, null,
            "Mavsumiy obuna", "Mavsum oxiri — obuna shu kungacha amal qiladi (taklif shu kuni yopiladi; bir oydan kam qolsa — koʻrinmaydi)"),

    NOTIFICATION_RATE("notification.rate-per-second", Type.INT, "20", 1, 30,
            "Bildirishnomalar", "Sekundiga nechta xabar yuboriladi (Telegram limiti ~30/s; kampaniya ham shu tezlikda)"),
    NOTIFICATION_MAX_ATTEMPTS("notification.max-attempts", Type.INT, "4", 1, 10,
            "Bildirishnomalar", "Yetkazib bo'lmasa necha marta urinadi (oraliq: 1, 5, 30 daqiqa, keyin har soat)"),

    ADVISOR_ENABLED("advisor.enabled", Type.BOOL, "true", null, null,
            "AI maslahat", "AI maslahatchi yoqilgan (ANTHROPIC_API_KEY bo'lmasa baribir ishlamaydi). Menyuda ko'rinishi — \"Bot menyusi\""),
    ADVISOR_MODEL("advisor.model", Type.MODEL, "claude-opus-5-5", null, null,
            "AI maslahat", "Claude modeli: claude-opus-5-5 (eng aniq, ~$0.03/rasm) yoki claude-haiku-5-5 (arzon, ~$0.001)"),
    ADVISOR_PAID_DAILY("advisor.paid-daily", Type.INT, "10", 1, 100,
            "AI maslahat", "Pullik tarif: fermer kuniga nechta rasm yubora oladi (AI javob bermagani hisoblanmaydi)"),
    ADVISOR_FREE_TOTAL("advisor.free-total", Type.INT, "1", 0, 20,
            "AI maslahat", "Bepul tarif: umrbod nechta (sinab ko'rish; 0 — faqat pullik)"),

    MONITORING_ENABLED("monitoring.enabled", Type.BOOL, "true", null, null,
            "Monitoring", "Avtomatik monitoring: har yangi sun'iy yo'ldosh suratida dala o'zi tekshiriladi"),
    MONITORING_PAID_ONLY("monitoring.paid-only", Type.BOOL, "true", null, null,
            "Monitoring", "Ogohlantirish xabari faqat pullik fermerlarga (bepul dalalar holati haftada bir yangilanadi — kartada ko'rinadi). O'chirilsa — hammaga xabar"),
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

    public enum Type { BOOL, INT, STRING, PLAN, MODEL, DATE }

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
