package uz.app.agro.bot;

// bot_states.step — STRING saqlanadi (tartib o'zgarsa ham tarix buzilmaydi)
public enum BotStep {
    ASK_CROP,           // eski (D2 gacha) — bazada qolgan holatlar ONBOARDING'ga o'tkaziladi
    ASK_IRRIGATION,
    ASK_WATER_SOURCE,
    ONBOARDING,         // admin sozlagan savol (data.ob — qadam kodi, data.obMode — start | profile)
    WAIT_POLYGON,       // Mini App orqali poligon (reply keyboard tugmasi)
    ANALYZING,          // worker tahlil qiladi
    REPORTED,           // hisobot yuborildi (S8: qo'shimcha savollar shu yerdan)
    FEEDBACK_TEXT,      // "💬 Fikr va yordam": birinchi xabar kutilmoqda; oldingi bosqich data.prevStep da
    FEEDBACK_CHAT,      // operator bilan suhbat: har xabar data.feedbackId suhbatiga qo'shiladi
    ADVISOR_PHOTO       // "🤖 AI maslahat": rasm kutilmoqda (data.advisorField — tanlangan dala, 0 — dalasiz)
}
