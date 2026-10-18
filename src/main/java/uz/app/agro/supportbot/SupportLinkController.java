package uz.app.agro.supportbot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profil → "Telegram'da bog'lash": bir martalik havola (support bot sozlanmagan bo'lsa — endpoint yo'q, 404) */
@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportLinkController {

    private final SupportLinkService links;

    @PostMapping("/telegram-link")
    public SupportLinkService.Link telegramLink() {
        return links.issue();
    }
}
