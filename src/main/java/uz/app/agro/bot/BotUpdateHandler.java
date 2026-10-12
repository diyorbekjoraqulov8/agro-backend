package uz.app.agro.bot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.analysis.AnalysisReportService;
import uz.app.agro.billing.PlanService;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerSnapshot;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.TelegramProfile;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.feedback.FeedbackBotService;
import uz.app.agro.feedback.FeedbackKind;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.feedback.FeedbackBotService.Attachment;
import uz.app.agro.field.Crop;
import uz.app.agro.file.FileProperties;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.field.FieldBotService.FieldCard;
import uz.app.agro.field.FieldIntakeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holat mashinasi: /start → onboarding (BotOnboarding: admin sozlagan xabar va savollar) → dala chizish → asosiy menyu.
 * Asosiy menyu (doimiy reply keyboard): 🗺 Yangi dala (Mini App) · 📋 Dalalarim · 💬 Fikr va yordam · 🌐 Til.
 * Dalalarim (inline): hisobot, qayta tahlil, chegarani tahrirlash (Mini App eski chegara bilan), o'chirish.
 * Fikr va yordam: tur (muammo/taklif/savol) → FEEDBACK_TEXT → birinchi xabar → FEEDBACK_CHAT (operator bilan
 * suhbat): har xabar suhbatga qo'shiladi, toki fermer yoki xodim yakunlamaguncha. Suhbat rejimidan chiqsa ham
 * suhbat ochiq qoladi — menyudan tashqari har qanday matn unga qo'shiladi. Yakunda — operatorni baholash.
 *
 * Tranzaksiya bu yerda OCHILMAYDI: har DB yozuvi service'ning public @Transactional metodida,
 * Telegram'ga HTTP chaqiruv esa tranzaksiyadan tashqarida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotUpdateHandler {

    private final FarmerRegistrationService farmers;
    private final BotStateService states;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotSender sender;
    private final FieldIntakeService fieldIntake;
    private final PlanService plans;
    private final FieldBotService fields;
    private final AnalysisReportService reports;
    private final BotReportSender reportSender;
    private final BotIrrigation irrigation;
    private final FeedbackBotService feedback;
    private final TelegramProperties props;
    private final BotFiles telegramFiles;
    private final BotReferral referralSection;
    private final BotOnboarding onboarding;
    private final BotMenu botMenu;
    private final FileRegistry fileRegistry;
    private final FileProperties fileProps;

    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public void handle(Update update) {
        // pengrad o'z thread'ida chaqiradi — MdcFilter bu yerga yetmaydi (CLAUDE.md 5.6)
        try {
            MDC.put("requestId", "tg-" + update.updateId());
            if (update.message() != null) {
                onMessage(update.message());
            } else if (update.callbackQuery() != null) {
                onCallback(update.callbackQuery());
            }
        } finally {
            MDC.clear();
        }
    }

    // ── Xabarlar ────────────────────────────────────────────────────────────

    private void onMessage(Message m) {
        User from = m.from();
        // Faqat shaxsiy chat: guruhga qo'shilsa ham u yerda savol-javob qilmaydi
        if (from == null || Boolean.TRUE.equals(from.isBot()) || m.chat() == null || m.chat().type() != Chat.Type.Private) {
            return;
        }
        MDC.put("userId", "tg:" + from.id());
        if (m.webAppData() != null) {
            onPolygon(from, m.chat().id(), m.webAppData().data());
            return;
        }
        // Rasm bilan kelgan izoh — caption (murojaatga skrinshot)
        String text = m.text() != null ? m.text().strip() : m.caption() != null ? m.caption().strip() : "";
        String command = m.text() == null ? "" : command(text);

        switch (command) {
            case "/start" -> start(from, m.chat().id(), argument(text));
            case "/lang" -> {
                String lang = farmers.find(from.id()).map(FarmerSnapshot::language)
                        .orElse(BotMessages.resolveLanguage(from.languageCode()));
                sender.send(m.chat().id(), messages.text(lang, "lang.choose"), keyboards.languages());
            }
            case "/help" -> {
                String lang = farmers.find(from.id()).map(FarmerSnapshot::language)
                        .orElse(BotMessages.resolveLanguage(from.languageCode()));
                sender.send(m.chat().id(), messages.text(lang, "help"), null);
            }
            case "/support" -> {
                Optional<FarmerSnapshot> farmer = farmers.find(from.id());
                if (farmer.isEmpty()) start(from, m.chat().id(), null);
                else openSupport(from.id(), m.chat().id(), farmer.get().language());
            }
            default -> {
                Optional<FarmerSnapshot> farmer = farmers.find(from.id());
                Optional<BotStep> step = states.current(from.id());
                if (farmer.isPresent() && step.isPresent() && step.get() == BotStep.FEEDBACK_TEXT) {
                    onFeedbackMessage(m, farmer.get().language(), text);
                    return;
                }
                if (farmer.isPresent() && step.isPresent() && step.get() == BotStep.FEEDBACK_CHAT) {
                    onChatMessage(m, farmer.get().language(), text);
                    return;
                }
                if (farmer.isPresent() && step.isPresent() && isQuestion(step.get()) && farmer.get().onboarded()
                        && isSectionButton(text)) {
                    // Ma'lumotlarini tahrirlayotgan edi — menyu tugmasi tahrirni to'xtatadi (eski javoblar saqlangan)
                    onboarding.stop(from.id(), m.chat().id(), farmer.get().language(), false);
                    onMenu(from.id(), m.chat().id(), farmer.get().language(), text);
                    return;
                }
                if (farmer.isPresent() && step.isPresent() && !isQuestion(step.get()) && onMenu(from.id(), m.chat().id(),
                        farmer.get().language(), text)) {
                    return;
                }
                if (farmer.isEmpty() || step.isEmpty()) {
                    start(from, m.chat().id(), null);  // birinchi xabari /start bo'lmasa ham
                } else {
                    // Tugma o'rniga matn yozdi — o'sha savolni qayta beramiz (restart'dan keyin ham)
                    String lang = farmer.get().language();
                    // Ochiq suhbat bor — operatorga javob deb qabul qilamiz ("✍️ Javob yozish" bosilmasa ham)
                    if (!isQuestion(step.get()) && routeToChat(m, lang, text)) return;
                    if (isQuestion(step.get())) {
                        sender.send(m.chat().id(), messages.text(lang, "use.buttons"), null);
                    }
                    ask(m.chat().id(), lang, step.get());
                }
            }
        }
    }

    private void start(User from, long chatId, String startParam) {
        String detected = BotMessages.resolveLanguage(from.languageCode());
        FarmerSnapshot f = farmers.start(new TelegramProfile(from.id(), chatId, from.username(),
                from.firstName(), from.lastName(), from.languageCode()), startParam, detected);
        if (f.created()) {
            log.info("yangi fermer id={} source={}", f.id(), startParam);
        }
        String lang = f.language();
        String name = firstName(from);
        long tg = from.id();
        if (!f.onboarded()) {
            // Yangi — xush kelibsiz va savollar; chala qolgan — birinchi javobsiz savoldan (boshidan emas)
            onboarding.begin(tg, chatId, lang, name, f);
            return;
        }
        // Ro'yxatdan o'tgan: savollar QAYTA berilmaydi (o'zgartirish — ⚙️ Sozlamalar → 📝 Ma'lumotlarim)
        states.leaveFeedback(tg);                     // murojaat rejimida bo'lsa — chiqadi (suhbat ochiq qoladi)
        BotStep cur = states.current(tg).orElse(null);
        boolean hasFields = !fields.list(tg).isEmpty();
        if (cur == null || isQuestion(cur) || cur == BotStep.WAIT_POLYGON) {
            states.moveTo(tg, hasFields ? BotStep.REPORTED : BotStep.WAIT_POLYGON);
        }
        if (hasFields) {
            sender.send(chatId, messages.text(lang, "start.welcomeBack", name.isEmpty() ? 0 : 1, name), menu(chatId, lang));
        } else {
            sender.send(chatId, messages.text(lang, "start.welcomeBack", name.isEmpty() ? 0 : 1, name), null);
            ask(chatId, lang, BotStep.WAIT_POLYGON);
        }
    }

    void ask(long chatId, String lang, BotStep step) {
        switch (step) {
            // Shaxsiy chatda chat_id == telegram_user_id
            case ONBOARDING, ASK_CROP, ASK_IRRIGATION, ASK_WATER_SOURCE -> onboarding.reask(chatId, chatId, lang, step);
            case WAIT_POLYGON -> sender.send(chatId, keyboards.miniAppConfigured()
                    ? question(lang, step) : messages.text(lang, "ask.polygon.soon"), menu(chatId, lang));
            case ANALYZING, REPORTED -> sender.send(chatId, question(lang, step), menu(chatId, lang));
            case FEEDBACK_TEXT -> sender.send(chatId, messages.text(lang, "feedback.ask."
                    + states.feedbackKind(chatId).orElse(FeedbackKind.QUESTION.name())), keyboards.cancelOnly(lang));
            case FEEDBACK_CHAT -> sender.send(chatId, messages.text(lang, "feedback.chat.hint",
                    states.chatFeedbackId(chatId).map(String::valueOf).orElse("")), keyboards.chatMenu(lang));
        }
    }

    /** Asosiy menyu klaviaturasini (qayta) ko'rsatish — tahrirlash bekor qilinganda, rad etilganda */
    public void showMenu(long chatId, String lang) {
        sender.send(chatId, messages.text(lang, "report.done"), menu(chatId, lang));
    }

    com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup menu(long chatId, String lang) {
        return botMenu.keyboard(chatId, lang);
    }

    private BigDecimal maxHa(long telegramUserId) {
        return botMenu.maxHa(telegramUserId);
    }

    // ── Menyu ───────────────────────────────────────────────────────────────

    /** @return true — matn menyu tugmasi edi va qayta ishlandi */
    private boolean onMenu(long tg, long chatId, String lang, String text) {
        if (labels("menu.myFields").contains(text)) {
            sendFieldList(tg, chatId, lang, null);
        } else if (labels("menu.feedback").contains(text)) {
            openSupport(tg, chatId, lang);
        } else if (labels("menu.friends").contains(text)) {
            referralSection.showFriends(tg, chatId, lang);
        } else if (labels("menu.settings").contains(text)) {
            referralSection.showSettings(chatId, lang);
        } else if (labels("menu.language").contains(text)) {      // eski klaviatura (C1 gacha) — hali telefonlarda bor
            sender.send(chatId, messages.text(lang, "lang.choose"), keyboards.languages());
        } else if (labels("menu.cancel").contains(text)) {
            showMenu(chatId, lang);
        } else if (labels("menu.newField").contains(text)) {
            // Mini App sozlanmagan — tugma oddiy matn yuboradi
            sender.send(chatId, messages.text(lang, "ask.polygon.soon"), menu(chatId, lang));
        } else {
            return false;
        }
        return true;
    }

    /** Asosiy menyu bo'limlari — murojaat/suhbat rejimida bosilsa rejimdan chiqaradi (matn murojaat bo'lib saqlanmaydi) */
    private boolean isSectionButton(String text) {
        for (String key : new String[]{"menu.myFields", "menu.newField", "menu.feedback", "menu.friends", "menu.settings", "menu.language"}) {
            if (labels(key).contains(text)) return true;
        }
        return false;
    }

    private Set<String> labels(String key) {
        return BotMessages.LANGUAGES.stream().map(l -> messages.text(l, key)).collect(Collectors.toSet());
    }

    /** @param editMessageId — "⬅️ Dalalarim" bosilganda o'sha xabarni almashtiramiz */
    private void sendFieldList(long tg, long chatId, String lang, Integer editMessageId) {
        List<FieldCard> list = fields.list(tg);
        String text = list.isEmpty() ? messages.text(lang, "fields.empty") : messages.text(lang, "fields.title", String.valueOf(list.size()));
        var kb = list.isEmpty() ? null : keyboards.fieldList(list);
        if (editMessageId != null) sender.edit(chatId, editMessageId, text, kb);
        else sender.send(chatId, text, kb);
    }

    private String cardText(String lang, FieldCard f) {
        String area = f.areaHa() != null ? ha(lang, f.areaHa().doubleValue()) + ("en".equals(lang) ? " ha" : " ga")
                : messages.text(lang, "field.area.unknown");
        if ("ru".equals(lang) && f.areaHa() != null) area = area.replace(" ga", " га");
        String last = f.lastJobStatus() == null ? messages.text(lang, "field.last.none")
                : messages.text(lang, "field.last." + f.lastJobStatus(),
                        f.lastJobAt() == null ? "" : f.lastJobAt().format(DAY_YEAR));
        return messages.text(lang, "field.card", f.name(), messages.label(lang, f.crop()), area, last);
    }

    /** Tugmali savol kutilmoqda (onboarding yoki eski ASK_* holati) — matn javob emas */
    private static boolean isQuestion(BotStep step) {
        return step == BotStep.ONBOARDING || BotOnboarding.isLegacy(step);
    }

    private String question(String lang, BotStep step) {
        return messages.text(lang, switch (step) {
            case ASK_CROP, ASK_IRRIGATION, ASK_WATER_SOURCE, ONBOARDING -> "use.buttons";
            case WAIT_POLYGON -> "ask.polygon";
            case ANALYZING -> "analysis.wait";
            case REPORTED -> "report.done";
            case FEEDBACK_TEXT -> "feedback.ask." + FeedbackKind.QUESTION.name();
            case FEEDBACK_CHAT -> "feedback.write";
        });
    }

    // ── Mini App (web_app_data) ─────────────────────────────────────────────

    private void onPolygon(User from, long chatId, String data) {
        if (farmers.find(from.id()).isEmpty()) {
            start(from, chatId, null);
            return;
        }
        submitPolygon(from.id(), chatId, data);
    }

    /**
     * Dala chegarasi — ikki yo'l bilan keladi: web_app_data (reply tugma, sendData) va HTTP (inline tugma,
     * MiniAppController — initData imzosi tekshirilgan). Natija fermerga bot xabari bilan boradi.
     * @return INVALID — chegara yaroqsiz; NOT_REGISTERED — fermer yo'q; aks holda qabul qilish natijasi
     */
    public String submitPolygon(long tg, long chatId, String data) {
        Optional<FarmerSnapshot> farmer = farmers.find(tg);
        if (farmer.isEmpty()) return "NOT_REGISTERED";
        String lang = farmer.get().language();
        BotPolygonParser.Parsed p = BotPolygonParser.parse(data, messages.text(lang, "polygon.defaultName"));
        if (p == null) {
            log.warn("dala chegarasi yaroqsiz ({} bayt)", data == null ? 0 : data.length());
            sender.send(chatId, messages.text(lang, "polygon.invalid"), null);
            return "INVALID";                        // reply tugma qoladi — qayta chizadi
        }
        boolean edit = p.fieldId() != null;
        FieldIntakeService.Result r = fieldIntake.submit(tg, p.name(), p.ring(), p.fieldId(), p.crop());
        log.info("dala {}: {} field={} job={} nuqta={}", edit ? "tahrir" : "qabul", r.outcome(), r.fieldId(), r.jobId(), p.ring().size() - 1);
        switch (r.outcome()) {
            case QUEUED -> {
                states.moveTo(tg, BotStep.ANALYZING);
                sender.send(chatId, messages.text(lang, edit ? "field.updatedQueued" : "polygon.received", p.name()), menu(chatId, lang));
            }
            case QUOTA_USED -> {
                states.moveTo(tg, BotStep.WAIT_POLYGON);
                String quota = r.plan().paid()
                        ? messages.text(lang, "polygon.quotaUsed.paid", p.name(), String.valueOf(r.plan().maxAnalyses()))
                        : messages.text(lang, "polygon.quotaUsed.free", p.name());
                sender.send(chatId, edit ? messages.text(lang, "field.updated", p.name()) + "\n" + quota : quota, menu(chatId, lang));
            }
            case AREA_LIMIT -> {
                // Klaviatura o'zgarmaydi (yangi dala yoki tahrirlash tugmasi qoladi) — kichikroq qilib qayta chizsin
                BigDecimal paidMax = plans.maxPaidFieldHa();
                String key = !r.plan().paid() && paidMax != null && paidMax.compareTo(r.plan().maxFieldHa()) > 0
                        ? "polygon.areaLimit.upgrade" : "polygon.areaLimit";
                sender.send(chatId, messages.text(lang, key, ha(lang, r.areaHa()), ha(lang, r.plan().maxFieldHa().doubleValue()),
                        paidMax == null ? "" : ha(lang, paidMax.doubleValue())), null);
            }
            case FIELD_LIMIT -> sender.send(chatId, messages.text(lang, "polygon.fieldLimit",
                    String.valueOf(r.plan().maxFields())), menu(chatId, lang));
            case BUSY -> sender.send(chatId, messages.text(lang, "field.busy"), menu(chatId, lang));
            case NOT_FOUND -> sender.send(chatId, messages.text(lang, "field.notFound"), menu(chatId, lang));
        }
        return r.outcome().name();
    }

    // ── Fikr va yordam ──────────────────────────────────────────────────────

    /**
     * FEEDBACK_TEXT: matn yoki rasm (izoh bilan) — murojaat. "Bekor qilish" yoki boshqa menyu tugmasi
     * — murojaatdan chiqadi (menyu matni murojaat bo'lib saqlanmasin).
     */
    private void onFeedbackMessage(Message m, String lang, String text) {
        long tg = m.from().id();
        long chatId = m.chat().id();
        if (labels("menu.cancel").contains(text)) {
            BotStep prev = states.leaveFeedback(tg);
            sender.send(chatId, messages.text(lang, "feedback.cancelled"), menu(chatId, lang));
            if (isQuestion(prev)) ask(chatId, lang, prev);
            return;
        }
        if (isSectionButton(text)) {
            states.leaveFeedback(tg);
            onMenu(tg, chatId, lang, text);
            return;
        }
        Incoming in = incoming(m, lang);
        if (in.rejected()) return;
        if (text.isEmpty() && in.att() == null) {        // stiker, ovozli xabar
            sender.send(chatId, messages.text(lang, "feedback.textOnly"), null);
            return;
        }
        FeedbackKind kind = states.feedbackKind(tg).map(k -> parse(FeedbackKind.class, k)).orElse(FeedbackKind.QUESTION);
        if (kind == null) kind = FeedbackKind.QUESTION;
        FeedbackBotService.Result r = feedback.submit(tg, kind, text, in.att());
        switch (r.outcome()) {
            case SAVED -> {
                log.info("murojaat #{} qabul qilindi: {} fayl={}", r.id(), kind, in.att() != null);
                // Suhbat darhol ochiladi: qo'shimcha xabar/rasm shu murojaatga, operator javobi shu yerga
                states.enterChat(tg, r.id());
                sender.send(chatId, messages.text(lang, "feedback.saved", String.valueOf(r.id())), keyboards.chatMenu(lang));
                notifyStaff(r, kind, text, in.att());
            }
            case APPENDED -> {                       // ochiq suhbati bor edi — unga qo'shildi
                states.enterChat(tg, r.id());
                sender.send(chatId, messages.text(lang, "feedback.appended", String.valueOf(r.id())), keyboards.chatMenu(lang));
            }
            case LIMIT -> {
                states.leaveFeedback(tg);
                sender.send(chatId, messages.text(lang, "feedback.limit"), menu(chatId, lang));
            }
            case EMPTY -> sender.send(chatId, messages.text(lang, "feedback.textOnly"), null);
            case CLOSED, NOT_FOUND -> start(m.from(), chatId, null);
        }
    }

    /** Menyu "💬 Fikr va yordam" / /support: ochiq suhbat bo'lsa — unga qaytadi, aks holda yangi murojaat turi */
    private void openSupport(long tg, long chatId, String lang) {
        Optional<FeedbackBotService.Active> active = feedback.active(tg);
        if (active.isPresent()) {
            states.enterChat(tg, active.get().id());
            sender.send(chatId, messages.text(lang, "feedback.chat.hint", String.valueOf(active.get().id())), keyboards.chatMenu(lang));
        } else {
            sender.send(chatId, messages.text(lang, "feedback.choose"), keyboards.feedbackKinds(lang));
        }
    }

    /**
     * FEEDBACK_CHAT: matn/rasm — suhbatga. "✅ Suhbatni yakunlash" — yopadi va baholash taklif qiladi.
     * "🏠 Asosiy menyu" va boshqa menyu tugmalari — rejimdan chiqadi, suhbat OCHIQ qoladi.
     */
    private void onChatMessage(Message m, String lang, String text) {
        long tg = m.from().id();
        long chatId = m.chat().id();
        Long id = states.chatFeedbackId(tg).orElse(null);
        if (labels("menu.endChat").contains(text)) {
            finishByFarmer(tg, chatId, lang, id);
            return;
        }
        if (labels("menu.home").contains(text) || labels("menu.cancel").contains(text)) {
            BotStep prev = states.leaveFeedback(tg);
            sender.send(chatId, messages.text(lang, "feedback.chat.left", id == null ? "" : String.valueOf(id)), menu(chatId, lang));
            if (isQuestion(prev)) ask(chatId, lang, prev);
            return;
        }
        if (isSectionButton(text)) {
            states.leaveFeedback(tg);
            onMenu(tg, chatId, lang, text);
            return;
        }
        Incoming in = incoming(m, lang);
        if (in.rejected()) return;
        if (text.isEmpty() && in.att() == null) {
            sender.send(chatId, messages.text(lang, "feedback.textOnly"), null);
            return;
        }
        onAppended(m, lang, feedback.append(tg, id, text, in.att()));
    }

    /** Suhbat rejimidan tashqarida yozilgan matn — ochiq suhbat bo'lsa unga. @return qabul qilindimi */
    private boolean routeToChat(Message m, String lang, String text) {
        if (text.isEmpty() && !hasFile(m)) return false;
        Optional<FeedbackBotService.Active> active = feedback.active(m.from().id());
        if (active.isEmpty()) return false;                 // fayl ochiq suhbat bo'lmasa yuklab olinmaydi
        Incoming in = incoming(m, lang);
        if (in.rejected()) return true;
        if (text.isEmpty() && in.att() == null) return false;
        FeedbackBotService.Result r = feedback.append(m.from().id(), active.get().id(), text, in.att());
        if (r.outcome() == FeedbackBotService.Outcome.APPENDED) {
            states.enterChat(m.from().id(), r.id());
            sender.send(m.chat().id(), messages.text(lang, "feedback.appended", String.valueOf(r.id())), keyboards.chatMenu(lang));
            return true;
        }
        onAppended(m, lang, r);
        return true;
    }

    private void onAppended(Message m, String lang, FeedbackBotService.Result r) {
        long tg = m.from().id();
        long chatId = m.chat().id();
        switch (r.outcome()) {
            case APPENDED -> sender.react(chatId, m.messageId(), "👌");   // matnli javob — chatni to'ldirmasin
            case LIMIT -> sender.send(chatId, messages.text(lang, "feedback.msgLimit"), null);
            case EMPTY -> sender.send(chatId, messages.text(lang, "feedback.textOnly"), null);
            default -> {                                  // CLOSED / NOT_FOUND — xodim yopgan
                states.leaveFeedback(tg);
                sender.send(chatId, messages.text(lang, "feedback.alreadyClosed"), menu(chatId, lang));
            }
        }
    }

    private void finishByFarmer(long tg, long chatId, String lang, Long feedbackId) {
        Optional<FeedbackBotService.Closed> closed = feedback.close(tg, feedbackId);
        BotStep prev = states.leaveFeedback(tg);
        if (closed.isEmpty()) {
            sender.send(chatId, messages.text(lang, "feedback.alreadyClosed"), menu(chatId, lang));
            return;
        }
        log.info("murojaat #{} fermer yakunladi", closed.get().id());
        sender.send(chatId, messages.text(lang, "feedback.closed.farmer", String.valueOf(closed.get().id())), menu(chatId, lang));
        if (closed.get().askRating()) askRating(chatId, lang, closed.get().id());
        if (isQuestion(prev)) ask(chatId, lang, prev);
    }

    void askRating(long chatId, String lang, long feedbackId) {
        sender.send(chatId, messages.text(lang, "feedback.rate.ask"), keyboards.rating(feedbackId));
    }

    /** Xodimlar guruhiga (sozlangan bo'lsa). Matn o'zbekcha — xodimlar tili */
    private void notifyStaff(FeedbackBotService.Result r, FeedbackKind kind, String text, Attachment att) {
        Long staffChat = props.supportChatId();
        if (staffChat == null) return;
        String who = (r.farmerName() == null ? "—" : r.farmerName())
                + (r.farmerUsername() == null ? "" : " (@" + r.farmerUsername() + ")");
        String body = messages.text("uz", "staff.feedback", String.valueOf(r.id()),
                messages.text("uz", "btn.fb." + kind.name()), who, text.isEmpty() ? "📎" : text);
        // Telegram'dagi rasm — file_id bilan qayta yubormasdan; hujjat — matnda belgi
        if (att != null && att.telegramFileId() != null) sender.sendPhoto(staffChat, att.telegramFileId(), body);
        else if (att != null) sender.send(staffChat, cut(body + "\n\n📎 Fayl — admin panelda"), null);
        else sender.send(staffChat, cut(body), null);
    }

    private static String cut(String s) {
        return s.length() <= 4096 ? s : s.substring(0, 4093) + "…";
    }

    // ── Fermer yuborgan fayl → server papkasi ───────────────────────────────

    private static final Set<String> DOCUMENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    /** att — saqlangan fayl (yoki yo'q); rejected — qabul qilinmadi, fermerga sababi aytildi */
    private record Incoming(Attachment att, boolean rejected) {}

    private static boolean hasFile(Message m) {
        return (m.photo() != null && m.photo().length > 0) || m.document() != null;
    }

    /**
     * Rasm: eng katta o'lcham (massiv oxiri) yuklab olinadi va serverga saqlanadi. Saqlab bo'lmasa ham
     * Telegram file_id qoladi — FeedbackFileBackfill keyingi ishga tushishda qayta urinadi (xabar yo'qolmaydi).
     * Hujjat: faqat JPG/PNG/PDF va hajm chegarasida; aks holda fermerga sababi aytiladi.
     */
    private Incoming incoming(Message m, String lang) {
        long chatId = m.chat().id();
        if (m.photo() != null && m.photo().length > 0) {
            String fileId = m.photo()[m.photo().length - 1].fileId();
            return new Incoming(new Attachment(saveFromTelegram(fileId, "telegram-photo.jpg"), fileId), false);
        }
        var d = m.document();
        if (d == null) return new Incoming(null, false);
        long max = fileProps.maxSize().toBytes();
        if (d.mimeType() == null || !DOCUMENT_TYPES.contains(d.mimeType()) || d.fileSize() == null || d.fileSize() > max) {
            sender.send(chatId, messages.text(lang, "feedback.fileUnsupported", String.valueOf(fileProps.maxSize().toMegabytes())), null);
            return new Incoming(null, true);
        }
        String hashId = saveFromTelegram(d.fileId(), d.fileName());
        if (hashId == null) {
            sender.send(chatId, messages.text(lang, "feedback.fileFailed"), null);
            return new Incoming(null, true);
        }
        return new Incoming(new Attachment(hashId, null), false);
    }

    /** @return files.hash_id yoki null (yuklab/o'qib bo'lmadi) */
    private String saveFromTelegram(String fileId, String name) {
        try {
            return telegramFiles.download(fileId)
                    .map(bytes -> fileRegistry.store(bytes, name, FileRegistry.Uploader.telegram()).hashId())
                    .orElse(null);
        } catch (BadRequestException e) {
            log.info("fermer fayli qabul qilinmadi: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("fermer fayli saqlanmadi: {}", e.getMessage());
            return null;
        }
    }

    // 12,4 (uz/ru) / 12.4 (en); butun bo'lsa kasrsiz
    private static String ha(String lang, double v) {
        String s = v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
        return "en".equals(lang) ? s : s.replace('.', ',');
    }

    // ── Tugmalar ────────────────────────────────────────────────────────────

    private void onCallback(CallbackQuery cb) {
        User from = cb.from();
        String data = cb.data();
        MaybeInaccessibleMessage msg = cb.maybeInaccessibleMessage();
        if (from == null || data == null || msg == null || msg.chat() == null) {
            sender.answer(cb.id(), null);
            return;
        }
        MDC.put("userId", "tg:" + from.id());
        long chatId = msg.chat().id();
        Optional<FarmerSnapshot> farmer = farmers.find(from.id());
        if (farmer.isEmpty()) {                       // baza tozalangan / eski tugma
            sender.answer(cb.id(), null);
            start(from, chatId, null);
            return;
        }
        String lang = farmer.get().language();

        // callback_data — ISHONCHSIZ kirish: noma'lum qiymat jim e'tiborsiz qoldiriladi
        if (onboarding.onCallback(cb.id(), from.id(), chatId, msg.messageId(), lang, data)) {
            // onboarding: ob:KOD:QIYMAT (qiymat bazadagi variantlar bilan tekshiriladi)
        } else if (data.startsWith(BotKeyboards.CROP) || data.startsWith(BotKeyboards.IRRIGATION) || data.startsWith(BotKeyboards.WATER)) {
            // Eski (D2 gacha) xabarlardagi tugmalar — onboarding orqali
            String code = data.startsWith(BotKeyboards.CROP) ? "CROP" : data.startsWith(BotKeyboards.IRRIGATION) ? "IRRIGATION" : "WATER_SOURCE";
            onboarding.onLegacyCallback(cb.id(), from.id(), chatId, msg.messageId(), lang, code, data.substring(2));
        } else if (data.startsWith(BotKeyboards.LANG)) {
            String newLang = data.substring(2);
            if (!BotMessages.LANGUAGES.contains(newLang)) { sender.answer(cb.id(), null); return; }
            farmers.setLanguage(from.id(), newLang);
            sender.answer(cb.id(), null);
            sender.edit(chatId, msg.messageId(), messages.text(newLang, "lang.saved"), null);
            states.current(from.id()).ifPresent(step -> ask(chatId, newLang, step));
        } else if (data.startsWith(BotKeyboards.FEEDBACK)) {
            FeedbackKind k = parse(FeedbackKind.class, data.substring(BotKeyboards.FEEDBACK.length()));
            sender.answer(cb.id(), null);
            if (k == null) return;
            states.enterFeedback(from.id(), k.name());
            sender.edit(chatId, msg.messageId(), messages.text(lang, "feedback.choose") + "\n\n✅ "
                    + messages.text(lang, "btn.fb." + k.name()), null);
            ask(chatId, lang, BotStep.FEEDBACK_TEXT);
        } else if (data.equals(BotReferral.SETTINGS + "profile")) {
            sender.answer(cb.id(), null);
            onboarding.beginProfile(from.id(), chatId, lang);
        } else if (referralSection.onCallback(cb.id(), from.id(), chatId, lang, data)) {
            // sozlamalar: st:plan, st:lang
        } else if (onChatCallback(cb, from.id(), chatId, msg.messageId(), lang, data)) {
            // suhbat: fw/fx/fs
        } else if (irrigation.onCallback(cb.id(), from.id(), chatId, msg.messageId(), lang, data)) {
            // sug'orish: wp/ws/wl/wi/wo
        } else if (!onFieldCallback(cb, from.id(), chatId, msg.messageId(), lang, data)) {
            sender.answer(cb.id(), null);
        }
    }

    /** Operator xabari ostidagi tugmalar va baholash. id — ishonchsiz: egalik FeedbackBotService'da */
    private boolean onChatCallback(CallbackQuery cb, long tg, long chatId, Integer messageId, String lang, String data) {
        if (data.startsWith(BotKeyboards.FEEDBACK_RATE)) {
            String[] p = data.substring(BotKeyboards.FEEDBACK_RATE.length()).split(":");
            long id;
            int stars;
            try {
                id = Long.parseLong(p[0]);
                stars = p.length == 2 ? Integer.parseInt(p[1]) : 0;
            } catch (NumberFormatException e) {
                sender.answer(cb.id(), null);
                return true;
            }
            if (feedback.rate(tg, id, stars)) {
                sender.answer(cb.id(), null);
                if (messageId != null) sender.edit(chatId, messageId, messages.text(lang, "feedback.rate.thanks", "⭐".repeat(stars)), null);
            } else {
                sender.answer(cb.id(), messages.text(lang, "feedback.rate.done"));
                if (messageId != null) sender.clearButtons(chatId, messageId);
            }
            return true;
        }
        boolean write = data.startsWith(BotKeyboards.FEEDBACK_WRITE);
        if (!write && !data.startsWith(BotKeyboards.FEEDBACK_END)) return false;
        sender.answer(cb.id(), null);
        long id;
        try {
            id = Long.parseLong(data.substring(3));
        } catch (NumberFormatException e) {
            return true;
        }
        Optional<FeedbackBotService.Active> active = feedback.active(tg).filter(a -> a.id() == id);
        if (active.isEmpty()) {                       // eski xabardagi tugma — suhbat yopilgan
            if (messageId != null) sender.clearButtons(chatId, messageId);
            sender.send(chatId, messages.text(lang, "feedback.alreadyClosed"), null);
            return true;
        }
        if (write) {
            states.enterChat(tg, id);
            sender.send(chatId, messages.text(lang, "feedback.write"), keyboards.chatMenu(lang));
        } else {
            if (messageId != null) sender.clearButtons(chatId, messageId);
            finishByFarmer(tg, chatId, lang, id);
        }
        return true;
    }

    /**
     * Dalalar va hisobot tugmalari. id'lar callback_data'dan — ISHONCHSIZ: har service egalikni
     * (dala/tahlil SHU Telegram foydalanuvchisiniki) tekshiradi, begonasi "topilmadi".
     */
    private boolean onFieldCallback(CallbackQuery cb, long tg, long chatId, Integer messageId, String lang, String data) {
        if (data.equals(BotKeyboards.FIELDS)) {
            sender.answer(cb.id(), null);
            sendFieldList(tg, chatId, lang, messageId);
            return true;
        }
        // "fk:<dala>:<CROP>" — ikki qismli, umumiy "prefiks:id" tahlilidan oldin
        if (data.startsWith(BotKeyboards.FIELD_CROP_SET)) {
            sender.answer(cb.id(), null);
            String[] p = data.substring(BotKeyboards.FIELD_CROP_SET.length()).split(":");
            Crop crop = p.length == 2 ? parse(Crop.class, p[1]) : null;
            long fieldId;
            try {
                fieldId = Long.parseLong(p[0]);
            } catch (NumberFormatException e) {
                return true;
            }
            if (crop == null) return true;
            fields.setCrop(tg, fieldId, crop).ifPresentOrElse(
                    name -> sender.edit(chatId, messageId, messages.text(lang, "field.cropChanged", name, messages.label(lang, crop)), null),
                    () -> sender.edit(chatId, messageId, messages.text(lang, "field.notFound"), null));
            return true;
        }
        int colon = data.indexOf(':');
        if (colon < 0) return false;
        String prefix = data.substring(0, colon + 1);
        long id;
        try {
            id = Long.parseLong(data.substring(colon + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        switch (prefix) {
            case BotKeyboards.FIELD_VIEW -> {
                sender.answer(cb.id(), null);
                fields.card(tg, id).ifPresentOrElse(f -> {
                    String water = irrigation.cardLine(lang, tg, id);
                    var kb = keyboards.fieldActions(lang, f);
                    var waterButtons = irrigation.cardButtons(lang, tg, id);
                    if (!waterButtons.isEmpty()) kb.addRow(waterButtons.toArray(com.pengrad.telegrambot.model.request.InlineKeyboardButton[]::new));
                    sender.edit(chatId, messageId, cardText(lang, f) + (water.isEmpty() ? "" : "\n" + water), kb);
                }, () -> sender.edit(chatId, messageId, messages.text(lang, "field.notFound"), null));
            }
            case BotKeyboards.FIELD_REPORT -> {
                sender.answer(cb.id(), null);
                Long jobId = fields.card(tg, id).map(FieldCard::lastDoneJobId).orElse(null);
                var report = jobId == null ? Optional.<AnalysisReportService.StoredReport>empty() : reports.forFarmer(tg, jobId);
                report.ifPresentOrElse(rep -> reportSender.send(chatId, lang, rep.input(), rep.result()),
                        () -> sender.send(chatId, messages.text(lang, "field.noReport"), null));
            }
            case BotKeyboards.FIELD_REANALYZE -> {
                sender.answer(cb.id(), null);
                var r = fieldIntake.reanalyze(tg, id);
                String name = fields.card(tg, id).map(FieldCard::name).orElse("");
                switch (r.outcome()) {
                    case QUEUED -> {
                        states.moveTo(tg, BotStep.ANALYZING);
                        sender.send(chatId, messages.text(lang, "field.reanalyzeQueued", name), null);
                    }
                    case BUSY -> sender.send(chatId, messages.text(lang, "field.busy"), null);
                    case QUOTA_USED -> sender.send(chatId, r.plan().paid()
                            ? messages.text(lang, "polygon.quotaUsed.paid", name, String.valueOf(r.plan().maxAnalyses()))
                            : messages.text(lang, "polygon.quotaUsed.free", name), null);
                    case AREA_LIMIT -> sender.send(chatId, messages.text(lang, "polygon.areaLimit", ha(lang, r.areaHa()),
                            ha(lang, r.plan().maxFieldHa().doubleValue()), ""), null);
                    default -> sender.send(chatId, messages.text(lang, "field.notFound"), null);
                }
            }
            case BotKeyboards.FIELD_EDIT -> {
                sender.answer(cb.id(), null);
                if (!keyboards.miniAppConfigured()) {
                    sender.send(chatId, messages.text(lang, "ask.polygon.soon"), null);
                    break;
                }
                fields.boundary(tg, id).ifPresentOrElse(
                        b -> sender.send(chatId, messages.text(lang, "field.editAsk", b.name()),
                                keyboards.editMap(lang, maxHa(tg), id, b.name(), b.crop(), b.ring())),
                        () -> sender.send(chatId, messages.text(lang, "field.notFound"), null));
            }
            case BotKeyboards.FIELD_CROP -> {
                sender.answer(cb.id(), null);
                fields.card(tg, id).ifPresentOrElse(
                        f -> sender.edit(chatId, messageId, messages.text(lang, "field.cropAsk", f.name()), keyboards.fieldCrops(lang, id)),
                        () -> sender.edit(chatId, messageId, messages.text(lang, "field.notFound"), null));
            }
            case BotKeyboards.FIELD_DELETE -> {
                sender.answer(cb.id(), null);
                fields.card(tg, id).ifPresentOrElse(
                        f -> sender.edit(chatId, messageId, messages.text(lang, "field.deleteConfirm", f.name()), keyboards.confirmDelete(lang, id)),
                        () -> sender.edit(chatId, messageId, messages.text(lang, "field.notFound"), null));
            }
            case BotKeyboards.FIELD_DELETE_YES -> {
                sender.answer(cb.id(), null);
                fields.delete(tg, id).ifPresentOrElse(
                        name -> sender.edit(chatId, messageId, messages.text(lang, "field.deleted", name), null),
                        () -> sender.edit(chatId, messageId, messages.text(lang, "field.notFound"), null));
            }
            case BotKeyboards.REPORT_PDF, BotKeyboards.REPORT_CSV -> {
                sender.answer(cb.id(), messages.text(lang, "file.preparing"));
                reports.forFarmer(tg, id).ifPresentOrElse(rep -> {
                    if (prefix.equals(BotKeyboards.REPORT_PDF)) reportSender.sendPdf(chatId, lang, rep.input(), rep.result());
                    else reportSender.sendCsv(chatId, lang, rep.input(), rep.result());
                }, () -> sender.send(chatId, messages.text(lang, "field.noReport"), null));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ── Yordamchilar ────────────────────────────────────────────────────────

    /** "/start@agroglobalbot kanal1" → "/start" */
    private static String command(String text) {
        if (!text.startsWith("/")) return "";
        String first = text.split("\\s+", 2)[0];
        int at = first.indexOf('@');
        return (at > 0 ? first.substring(0, at) : first).toLowerCase();
    }

    private static String argument(String text) {
        String[] parts = text.split("\\s+", 2);
        return parts.length > 1 ? parts[1].strip() : null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstName(User u) {
        return u.firstName() == null || u.firstName().isBlank() ? "" : u.firstName().strip();
    }
}
