-- Pro sinovi: tizim beradigan bepul obuna (summa 0), REFERRAL kabi — xodim qo'lda yozmaydi
ALTER TABLE farmer_subscriptions DROP CONSTRAINT ck_subscriptions_method;
ALTER TABLE farmer_subscriptions
    ADD CONSTRAINT ck_subscriptions_method CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'OTHER', 'REFERRAL', 'TRIAL'));

-- Kunlik eslatma qidiruvi (TrialService.sendReminders: tugash sanasi oralig'i)
CREATE INDEX idx_subscriptions_trial_until ON farmer_subscriptions (valid_until) WHERE payment_method = 'TRIAL';

-- Takror yubormaslik tekshiruvi (NotificationRepository.existsByRefTypeAndRefId) — jadval kampaniyalar bilan tez o'sadi
CREATE INDEX idx_notifications_ref ON notifications (ref_type, ref_id) WHERE ref_type IS NOT NULL;
