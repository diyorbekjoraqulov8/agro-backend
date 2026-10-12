package uz.app.agro.referral.dto;

import java.time.LocalDateTime;

public record ReferrerRow(Long farmerId, String farmerName, String farmerUsername, String code,
                          long invited, long activated, long rewards, LocalDateTime lastInvitedAt) {}
