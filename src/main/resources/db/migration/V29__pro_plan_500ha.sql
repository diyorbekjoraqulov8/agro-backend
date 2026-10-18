-- Pro tarif: bitta dala 500 gektargacha (Python tahlil xizmatining texnik chegarasi — 500).
-- Faqat boshlang'ich qiymat (100) bo'lsa: admin o'zi o'zgartirgan qiymatga tegilmaydi
UPDATE plans SET max_field_ha = 500, description = REPLACE(description, '100 ga gacha', '500 gektargacha')
WHERE code = 'MONTHLY' AND max_field_ha = 100;
