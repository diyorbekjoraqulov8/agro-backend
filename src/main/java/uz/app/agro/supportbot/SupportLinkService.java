package uz.app.agro.supportbot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CurrentActor;
import uz.app.agro.user.UserEntity;
import uz.app.agro.user.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xodimning Telegram'ini bog'lash — EGALIK isbotlanadi: admin panel bir martalik havola beradi
 * (t.me/<support_bot>?start=link_<kod>, 10 daqiqa), xodim uni o'z Telegram'ida ochadi — bot from.id ni o'zi yozadi.
 * Qo'lda ID kiritish yo'q: aks holda boshqa xodimning ID'sini egallab, uning guruhdagi javoblarini o'z nomiga yozdirish mumkin edi.
 * Kodlar xotirada (restart — yangi havola olinadi).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportLinkService {

    static final String START_PREFIX = "link_";
    private static final long TTL_SECONDS = 600;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();   // chalkash 0/O, 1/I yo'q
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Link(String url, Instant expiresAt) {}

    public enum Outcome { LINKED, EXPIRED, TAKEN, USER_GONE }

    private record Pending(long userId, Instant expiresAt) {}

    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final SupportBotClient client;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** Guruhdan javob berish huquqi borlar uchun (feedback:reply) */
    @PreAuthorize(Permissions.CAN_REPLY_FEEDBACK)
    public Link issue() {
        AuthUser me = CurrentActor.get().orElseThrow(() -> new IllegalStateException("autentifikatsiya yo'q"));
        String username = client.username().orElseThrow(() -> new BadRequestException("Support bot javob bermayapti — keyinroq urinib ko'ring"));
        Instant now = Instant.now();
        codes.values().removeIf(p -> p.expiresAt().isBefore(now));
        codes.entrySet().removeIf(e -> e.getValue().userId() == me.id());      // eski havola bekor
        String code = code();
        Instant expires = now.plusSeconds(TTL_SECONDS);
        codes.put(code, new Pending(me.id(), expires));
        return new Link("https://t.me/" + username + "?start=" + START_PREFIX + code, expires);
    }

    /** Bot: /start link_<kod> (shaxsiy chat). Kod bir martalik */
    @Transactional
    public Outcome confirm(String code, long telegramUserId) {
        Pending p = codes.get(code);
        if (p == null || p.expiresAt().isBefore(Instant.now())) {
            codes.remove(code);
            return Outcome.EXPIRED;
        }
        UserEntity u = userRepository.findById(p.userId()).filter(UserEntity::isActive).orElse(null);
        if (u == null) {
            codes.remove(code);
            return Outcome.USER_GONE;
        }
        // Band akkauntdan ochildi — kod yonmaydi: egasi to'g'ri Telegram'dan qayta ochishi mumkin
        if (userRepository.existsByTelegramUserIdAndIdNot(telegramUserId, u.getId())) return Outcome.TAKEN;
        if (!codes.remove(code, p)) return Outcome.EXPIRED;                 // parallel ikki /start — bittasi
        Long old = u.getTelegramUserId();
        u.setTelegramUserId(telegramUserId);
        auditService.event(AuditEvent.USER_TELEGRAM_LINKED).actor(u.getId(), u.getEmail()).target("USER", u.getId())
                .detail("old", old).detail("new", telegramUserId).record();
        return Outcome.LINKED;
    }

    String emailOf(long telegramUserId) {
        return userRepository.findWithPermissionsByTelegramUserId(telegramUserId).map(UserEntity::getEmail).orElse("");
    }

    private static String code() {
        char[] c = new char[10];
        for (int i = 0; i < c.length; i++) c[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new String(c);
    }
}
