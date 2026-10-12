package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerSnapshot;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.field.Crop;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.onboarding.OnboardingService;
import uz.app.agro.onboarding.OnboardingStepEntity;
import uz.app.agro.onboarding.OnboardingService.Step;
import uz.app.agro.onboarding.dto.StepOption;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Onboarding oqimi — qadamlar bazada (admin → Onboarding). Ikki rejim:
 *   start   — ro'yxatdan o'tish: xabarlar + javobsiz savollar → FINISH ("dalani belgilang" + inline 🗺 tugma);
 *   profile — ⚙️ → 📝 Ma'lumotlarim: faqat savollar (hammasi) → "saqlandi".
 * Holat: BotStep.ONBOARDING, data.ob — joriy savol kodi. Eski holat (ASK_*) va eski tugmalar (c:/i:/w:) ham ishlaydi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotOnboarding {

    static final String START = "start", PROFILE = "profile";
    // Eski (D2 gacha) holat va callback'lar → o'rnatilgan savol kodi
    private static final Map<BotStep, String> LEGACY_STEP = Map.of(
            BotStep.ASK_CROP, "CROP", BotStep.ASK_IRRIGATION, "IRRIGATION", BotStep.ASK_WATER_SOURCE, "WATER_SOURCE");

    private final OnboardingService onboarding;
    private final BotStateService states;
    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotMenu menu;
    private final FarmerRegistrationService farmers;
    private final FieldBotService fields;

    static boolean isLegacy(BotStep step) {
        return LEGACY_STEP.containsKey(step);
    }

    /** /start: yangi — boshidan (xush kelibsiz bilan); chala qolgan — birinchi javobsiz savoldan */
    public void begin(long tg, long chatId, String lang, String name, FarmerSnapshot f) {
        List<Step> flow = onboarding.flow();
        Map<String, String> answers = onboarding.answers(tg);
        int from = 0;
        if (!f.created()) {
            for (int i = 0; i < flow.size(); i++) {
                if (flow.get(i).question() && !onboarding.answered(flow.get(i), f, answers)) { from = i; break; }
            }
        }
        run(tg, chatId, lang, name, START, flow, from);
    }

    /** ⚙️ → 📝 Ma'lumotlarim: barcha savollar qaytadan (eski javoblar menyu bosilsa saqlanib qoladi) */
    public void beginProfile(long tg, long chatId, String lang) {
        sender.send(chatId, messages.text(lang, "profile.edit"), null);
        run(tg, chatId, lang, "", PROFILE, onboarding.flow(), firstQuestion(onboarding.flow()));
    }

    /**
     * Ketma-ket: xabarlar yuboriladi, birinchi so'raladigan savolda to'xtaydi (javobni kutadi).
     * start: javob berilgan savollar o'tkaziladi; profile: xabarlar o'tkaziladi, barcha savollar so'raladi.
     */
    private void run(long tg, long chatId, String lang, String name, String mode, List<Step> flow, int from) {
        FarmerSnapshot f = farmers.find(tg).orElse(null);
        if (f == null) return;
        Map<String, String> answers = onboarding.answers(tg);
        for (int i = Math.max(0, from); i < flow.size(); i++) {
            Step s = flow.get(i);
            if (!s.question()) {
                if (PROFILE.equals(mode)) continue;
                if (OnboardingStepEntity.FINISH.equals(s.code())) {
                    finish(tg, chatId, lang, name, s);
                    return;
                }
                sender.send(chatId, render(s.text(lang), name), null);
                continue;
            }
            if (START.equals(mode) && onboarding.answered(s, f, answers)) continue;
            states.enterOnboarding(tg, s.code(), mode);
            ask(chatId, lang, s, flow);
            return;
        }
        // Savollar tugadi (FINISH yo'q yoki profile rejimi)
        if (PROFILE.equals(mode)) stop(tg, chatId, lang, true);
        else finish(tg, chatId, lang, name, null);
    }

    private void ask(long chatId, String lang, Step s, List<Step> flow) {
        List<Step> questions = flow.stream().filter(Step::question).toList();
        int n = questions.indexOf(s) + 1;
        String prefix = questions.size() > 1 && n > 0 ? n + "/" + questions.size() + ". " : "";
        sender.send(chatId, prefix + s.text(lang), keyboards.onboardingOptions(s.code(), s.values(), labels(lang, s)));
    }

    /** Joriy savolni qayta berish — matn yozganda, tilni o'zgartirganda; eski holat (ASK_*) shu yerda o'tkaziladi */
    public void reask(long tg, long chatId, String lang, BotStep step) {
        Optional<BotStateService.OnboardingState> st = states.onboarding(tg);
        String code = st.map(BotStateService.OnboardingState::code).orElse(LEGACY_STEP.get(step));
        String mode = st.map(BotStateService.OnboardingState::mode).orElse(START);
        List<Step> flow = onboarding.flow();
        Optional<Step> s = flow.stream().filter(x -> x.code().equals(code) && x.question()).findFirst();
        if (s.isEmpty()) {                            // admin o'chirgan savol — keyingisidan davom
            run(tg, chatId, lang, "", mode, flow, firstQuestion(flow));
            return;
        }
        states.enterOnboarding(tg, code, mode);
        ask(chatId, lang, s.get(), flow);
    }

    /** "ob:KOD:QIYMAT" — @return true: shu bo'lim tugmasi edi */
    public boolean onCallback(String callbackId, long tg, long chatId, Integer messageId, String lang, String data) {
        if (!data.startsWith(BotKeyboards.ONBOARDING)) return false;
        String[] p = data.substring(BotKeyboards.ONBOARDING.length()).split(":", 2);
        answer(callbackId, tg, chatId, messageId, lang, p[0], p.length == 2 ? p[1] : null);
        return true;
    }

    /** Eski xabardagi c:/i:/w: tugmalari */
    public void onLegacyCallback(String callbackId, long tg, long chatId, Integer messageId, String lang, String code, String value) {
        answer(callbackId, tg, chatId, messageId, lang, code, value);
    }

    private void answer(String callbackId, long tg, long chatId, Integer messageId, String lang, String code, String value) {
        sender.answer(callbackId, null);
        // callback_data — ishonchsiz: savol va qiymat bazadagi variantlardan biri bo'lishi shart
        Optional<Step> step = onboarding.step(code);
        if (step.isEmpty() || !onboarding.answer(tg, code, value)) return;
        Step s = step.get();
        if (messageId != null) {
            int idx = s.values().indexOf(value);
            sender.edit(chatId, messageId, s.text(lang) + "\n\n✅ " + labels(lang, s).get(idx), (InlineKeyboardMarkup) null);
        }
        // Faqat joriy savolga javob oqimni siljitadi (eski xabardagi tugma — qiymat yangilanadi, xolos)
        Optional<BotStateService.OnboardingState> st = states.onboarding(tg);
        BotStep cur = states.current(tg).orElse(null);
        boolean current = st.map(x -> x.code().equals(code)).orElse(false) || code.equals(LEGACY_STEP.get(cur));
        if (!current) return;
        String mode = st.map(BotStateService.OnboardingState::mode).orElse(START);
        List<Step> flow = onboarding.flow();
        int i = 0;
        while (i < flow.size() && !flow.get(i).code().equals(code)) i++;
        run(tg, chatId, lang, "", mode, flow, i + 1);
    }

    /**
     * Yakun: "dalani belgilang" + inline 🗺 (Mini App → HTTP). Inline sozlanmagan bo'lsa — matn ostida pastki menyu
     * (🗺 Yangi dala tugmasi). Ikkala holatda ham fermer keyingi qadamni ko'radi — oqim to'xtab qolmaydi.
     */
    private void finish(long tg, long chatId, String lang, String name, Step finishStep) {
        boolean hasFields = !fields.list(tg).isEmpty();
        states.moveTo(tg, hasFields ? BotStep.REPORTED : BotStep.WAIT_POLYGON);
        String text = finishStep != null ? render(finishStep.text(lang), name) : messages.text(lang, "ask.polygon");
        InlineKeyboardMarkup inline = menu.mapInline(chatId, lang);
        if (inline != null) {
            sender.send(chatId, text, inline);
            sender.send(chatId, messages.text(lang, "onboarding.menuHint"), menu.keyboard(chatId, lang));
        } else {
            sender.send(chatId, text + "\n\n" + messages.text(lang, "onboarding.useMenuButton"), menu.keyboard(chatId, lang));
        }
        log.info("onboarding tugadi (tg {}), inline={}", tg, inline != null);
    }

    /** Tahrir to'xtadi (menyu bosildi yoki savollar tugadi) — dalasi bor: menyu, yo'q: dala chizish */
    public void stop(long tg, long chatId, String lang, boolean announce) {
        boolean hasFields = !fields.list(tg).isEmpty();
        states.moveTo(tg, hasFields ? BotStep.REPORTED : BotStep.WAIT_POLYGON);
        if (!announce) return;
        if (hasFields) sender.send(chatId, messages.text(lang, "profile.saved"), menu.keyboard(chatId, lang));
        else finish(tg, chatId, lang, "", null);
    }

    private List<String> labels(String lang, Step s) {
        if (s.target() == null) {
            return s.options().stream().map(o -> optionLabel(o, lang)).toList();
        }
        return switch (s.target()) {
            case MAIN_CROP -> Arrays.stream(Crop.values()).map(v -> messages.label(lang, v)).toList();
            case IRRIGATION_TYPE -> Arrays.stream(IrrigationType.values()).map(v -> messages.label(lang, v)).toList();
            case WATER_SOURCE -> Arrays.stream(WaterSource.values()).map(v -> messages.label(lang, v)).toList();
        };
    }

    private static String optionLabel(StepOption o, String lang) {
        String l = o.labels().get(lang);
        return l != null && !l.isBlank() ? l : o.labels().getOrDefault("uz", o.value());
    }

    private static int firstQuestion(List<Step> flow) {
        for (int i = 0; i < flow.size(); i++) if (flow.get(i).question()) return i;
        return flow.size();
    }

    /** {name} — fermer ismi; ism yo'q bo'lsa ", {name}" ham olib tashlanadi ("Salom, !" emas) */
    private static String render(String text, String name) {
        if (name == null || name.isBlank()) return text.replace(", {name}", "").replace("{name}", "");
        return text.replace("{name}", name);
    }
}
