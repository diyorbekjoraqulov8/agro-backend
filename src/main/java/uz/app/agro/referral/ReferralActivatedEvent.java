package uz.app.agro.referral;

/** Taklif qilingan fermer faollashdi (birinchi tahlili tayyor) — mukofot hisobi commit'dan keyin */
public record ReferralActivatedEvent(long referrerId, long friendId) {}
