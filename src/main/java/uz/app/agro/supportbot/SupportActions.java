package uz.app.agro.supportbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uz.app.agro.common.exception.AppException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.security.AuthUser;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Guruhdagi xodim amali — SHU xodim nomidan: SecurityContext (huquqlar DB'dan) → service'lardagi @PreAuthorize ishlaydi.
 * Kontekst va FROM_TELEGRAM har doim finally'da tozalanadi (pengrad thread'lari qayta ishlatiladi).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportActions {

    private static final Locale UZ = Locale.forLanguageTag("uz");

    private final MessageSource messages;

    /** @return null — bajarildi; aks holda xodimga ko'rsatiladigan sabab */
    public String runAs(SupportStore.Staff staff, Consumer<AuthUser> action) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(staff.authentication());
        SecurityContextHolder.setContext(context);
        SupportBridge.FROM_TELEGRAM.set(true);
        try {
            action.accept(staff.user());
            return null;
        } catch (AccessDeniedException e) {
            return text("support.denied");
        } catch (NotFoundException e) {
            // Begona suhbat (operator, manage yo'q) — IDOR 404; xodimga tushunarli sabab
            return text("support.notYours");
        } catch (AppException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            log.error("support bot: xodim amali bajarilmadi", e);
            return text("support.failed");
        } finally {
            SupportBridge.FROM_TELEGRAM.remove();
            SecurityContextHolder.clearContext();
        }
    }

    String text(String key, Object... args) {
        return messages.getMessage(key, args, UZ);
    }
}
