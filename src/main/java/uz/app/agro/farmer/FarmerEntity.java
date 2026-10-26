package uz.app.agro.farmer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import uz.app.agro.common.BaseEntity;
import uz.app.agro.field.Crop;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Fermer users'da EMAS: login qilmaydi, identifikatori — telegram_user_id
@Entity
@Table(name = "farmers")
@Getter
@Setter
@SQLDelete(sql = "UPDATE farmers SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class FarmerEntity extends BaseEntity {

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 20)
    private String phone;

    /** Support guruhidagi mavzu (message_thread_id) va o'sha guruh — V37; null — hali yozmagan yoki support bot yo'q */
    @Column(name = "support_chat_id")
    private Long supportChatId;

    @Column(name = "support_topic_id")
    private Long supportTopicId;

    @Column(nullable = false, length = 5)
    private String language = "uz";

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_crop", length = 32)
    private Crop mainCrop;

    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_type", length = 32)
    private IrrigationType irrigationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_source", length = 32)
    private WaterSource waterSource;

    @Column(name = "total_area_ha", precision = 10, scale = 2)
    private BigDecimal totalAreaHa;

    @Column(length = 64)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_status", nullable = false, length = 16)
    private LeadStatus leadStatus = LeadStatus.NONE;

    @Column(name = "lead_note", columnDefinition = "text")
    private String leadNote;

    @Column(name = "blocked_bot", nullable = false)
    private boolean blockedBot = false;

    /** Lab dalalari egasi (V41, bitta) — Telegram'siz: xabarlari yuborilmaydi, statistika/kampaniyaga kirmaydi */
    @Column(nullable = false, updatable = false)
    private boolean lab = false;

    // ── Taklif (referral) ──
    /** Shaxsiy taklif kodi — t.me/bot?start=ref_<kod>. Birinchi so'ralganda yasaladi */
    @Column(name = "referral_code", length = 16)
    private String referralCode;

    /** Kim taklif qilgan — faqat YANGI fermer /start ref_... bilan kelganda, bir marta */
    @Column(name = "referred_by_id", updatable = false)
    private Long referredById;

    @Column(name = "referred_at", updatable = false)
    private LocalDateTime referredAt;

    /** Birinchi tahlili tayyor bo'lgan vaqt — taklif shundan keyin "faol" hisoblanadi (soxta akkauntlar sanalmasin) */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
