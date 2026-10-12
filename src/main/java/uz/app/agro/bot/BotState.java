package uz.app.agro.bot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Fermer qaysi savolda turgani. DB'da — restart'da savol o'rtasida qolib ketmasin
@Entity
@Table(name = "bot_states")
@Getter
@Setter
public class BotState {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BotStep step;

    // Keyingi bosqichlar uchun vaqtinchalik qiymatlar (masalan dala nomi)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
