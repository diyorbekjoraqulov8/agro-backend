-- Obuna so'rovlari (Pro olish, Agro Biznes arizasi) — murojaatlar ichida alohida tur: admin'da o'z tabi
ALTER TABLE feedback DROP CONSTRAINT ck_feedback_kind;
ALTER TABLE feedback ADD CONSTRAINT ck_feedback_kind CHECK (kind IN ('PROBLEM', 'SUGGESTION', 'QUESTION', 'SUBSCRIPTION'));
