package uz.app.agro.referral;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Berilgan mukofot — o'zgarmas yozuv. referrals_used: shu mukofot uchun "ishlatilgan" faol do'stlar soni
@Entity
@Table(name = "referral_rewards")
@Getter
@Setter
public class ReferralRewardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, updatable = false)
    private Long farmerId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "referrals_used", nullable = false, updatable = false)
    private int referralsUsed;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();
}
