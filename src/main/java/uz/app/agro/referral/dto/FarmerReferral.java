package uz.app.agro.referral.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Fermer sahifasi: uning kodi, kim taklif qilgani, kimlarni taklif qilgani va olgan mukofotlari */
public record FarmerReferral(
        String code, Person referredBy, LocalDateTime referredAt, LocalDateTime activatedAt,
        List<Invited> invited, List<Reward> rewards
) {
    public record Person(Long id, String name, String username) {}
    public record Invited(Long id, String name, String username, LocalDateTime referredAt, LocalDateTime activatedAt) {}
    public record Reward(Long subscriptionId, String planName, LocalDate validFrom, LocalDate validUntil,
                         int referralsUsed, LocalDateTime createdDate) {}
}
