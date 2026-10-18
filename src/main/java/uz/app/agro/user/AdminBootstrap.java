package uz.app.agro.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.rbac.RoleNames;
import uz.app.agro.rbac.RoleRepository;

/**
 * Prod'da birinchi admin: ADMIN_EMAIL + ADMIN_PASSWORD berilgan va bunday email yo'q bo'lsa — ADMIN roli bilan yaratiladi.
 * Dev seed (db/seed) prodda yo'q, ochiq ro'yxatdan o'tish o'chiq — boshqa yo'l bilan birinchi xodim paydo bo'lmaydi.
 * Email allaqachon bor — hech narsa qilinmaydi (parol env orqali qayta yozilmaydi).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD = 12;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final AuditService audit;

    @Value("${app.bootstrap-admin.email:}")
    private String email;

    @Value("${app.bootstrap-admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;
        if (password.length() < MIN_PASSWORD) {
            log.warn("ADMIN_PASSWORD {} belgidan qisqa — birinchi admin yaratilmadi", MIN_PASSWORD);
            return;
        }
        String normalized = email.strip().toLowerCase();
        // Noto'g'ri qiymat ilovani yiqitmasin — faqat ogohlantirish (admin keyin to'g'rilaydi)
        if (normalized.length() > 120 || !normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            log.warn("ADMIN_EMAIL noto'g'ri shaklda — birinchi admin yaratilmadi");
            return;
        }
        tx.executeWithoutResult(status -> {
            if (users.existsByEmail(normalized)) return;
            UserEntity user = new UserEntity();
            user.setEmail(normalized);
            user.setPassword(passwordEncoder.encode(password));
            user.getRoles().add(roles.findByName(RoleNames.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("ADMIN roli topilmadi — migratsiya buzuq")));
            users.save(user);
            audit.event(AuditEvent.USER_REGISTERED).actor(null, "system").target("USER", user.getId())
                    .detail("bootstrap", "ADMIN_EMAIL").record();
            log.warn("Birinchi admin yaratildi: {} — kirib parolni almashtiring, ADMIN_PASSWORD ni env'dan olib tashlang", normalized);
        });
    }
}
