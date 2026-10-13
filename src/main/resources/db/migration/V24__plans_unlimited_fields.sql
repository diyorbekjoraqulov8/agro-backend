-- Cheksiz tarif: max_fields NULL — dala soni cheklanmaydi (max_analyses kabi).
-- max_field_ha cheksiz bo'lmaydi — Python tahlil xizmatining texnik chegarasi (500 ga, PlanRequest tekshiradi).
ALTER TABLE plans ALTER COLUMN max_fields DROP NOT NULL;
ALTER TABLE plans DROP CONSTRAINT ck_plans_limits;
ALTER TABLE plans ADD CONSTRAINT ck_plans_limits
    CHECK (max_field_ha > 0 AND (max_fields IS NULL OR max_fields > 0) AND (max_analyses IS NULL OR max_analyses >= 0));
