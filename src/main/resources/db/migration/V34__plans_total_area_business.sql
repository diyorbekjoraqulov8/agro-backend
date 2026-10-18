-- Tarif limiti endi JAMI maydon bo'yicha (bitta dala chegarasi — Python tahlil xizmatining texnik chegarasi 500 ga, qoladi).
-- max_total_ha NULL — cheksiz. contact_sales — narx ko'rsatilmaydi, botda "ariza qoldirish" (Agro Biznes).
ALTER TABLE plans
    ADD COLUMN max_total_ha  NUMERIC(10, 2),
    ADD COLUMN contact_sales BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE plans
    ADD CONSTRAINT ck_plans_total_ha CHECK (max_total_ha IS NULL OR max_total_ha > 0),
    ADD CONSTRAINT ck_plans_contact_sales CHECK (NOT (system_plan AND contact_sales));

-- Taxminiy maydon dala chizilganda yoziladi (tahlil aniqlaydi). Eski, hali tahlil qilinmagan dalalar — 0 hisoblanadi
UPDATE plans SET max_total_ha = 20 WHERE code = 'FREE' AND max_total_ha IS NULL;

-- Pro: jami 200 gektar. Bitta dala va narx — faqat boshlang'ich qiymatlar bo'lsa (V12: 100 ga / 0 so'm, V29: 500 ga,
-- ilk sozlash: 30 000 so'm); admin o'zi qo'ygan qiymatga tegilmaydi
UPDATE plans SET max_total_ha = 200 WHERE code = 'MONTHLY';
UPDATE plans SET max_field_ha = 200 WHERE code = 'MONTHLY' AND max_field_ha IN (100, 500);
UPDATE plans SET price = 59000 WHERE code = 'MONTHLY' AND price IN (0, 30000);
UPDATE plans SET description = 'Jami 200 gektargacha, tahlil cheklanmagan'
WHERE code = 'MONTHLY' AND description IN ('30 kun, 100 ga gacha, tahlil cheklanmagan', '30 kun, 500 gektargacha, tahlil cheklanmagan',
                                           '30 kun, 100 gacha, tahlil cheklanmagan');

-- Agro Biznes: maydon cheksiz, narx kelishiladi (to'lov admin paneldan, kelishilgan summa bilan)
INSERT INTO plans (code, name, description, price, currency, duration_days, max_field_ha, max_fields, max_analyses,
                   system_plan, active, max_total_ha, contact_sales)
VALUES ('BUSINESS', 'Agro Biznes', 'Katta xo''jaliklar: narx kelishiladi, maydon cheksiz, shaxsiy menejer',
        0, 'UZS', 30, 500, NULL, NULL, FALSE, TRUE, NULL, TRUE)
ON CONFLICT (code) DO NOTHING;
