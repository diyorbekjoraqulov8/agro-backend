-- L1: Lab dalalari — admin xaritada chizgan test dalalari. Egasi — tizimning o'z "fermeri" (Telegram'siz):
-- dala fermer dalasi bilan BIR XIL yo'ldan o'tadi (mavsum, tahlil, monitoring, ob-havo), xabarlar esa
-- Telegram'ga yuborilmaydi — NotificationDispatcher SKIPPED qiladi, admin "Xabarlar"da ko'radi.
ALTER TABLE farmers ADD COLUMN lab BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uk_farmers_lab ON farmers (lab) WHERE lab = TRUE AND deleted = FALSE;

-- telegram_user_id = -1: foydalanuvchi id'lari musbat — bot orqali hech kim bu fermer bo'la olmaydi;
-- chat_id = -1 (guruh id'lari manfiy, lekin -100... ko'rinishida) — real chat emas
INSERT INTO farmers (telegram_user_id, chat_id, full_name, language, lead_status, blocked_bot, lab, created_date, updated_date)
SELECT -1, -1, 'Lab (admin dalalari)', 'uz', 'NONE', FALSE, TRUE, now(), now()
 WHERE NOT EXISTS (SELECT 1 FROM farmers WHERE lab = TRUE AND deleted = FALSE);

-- Cheksiz tarif (Agro Biznes): monitoring to'liq rejimda, dala/maydon limiti fermernikidek.
-- Tarif yo'q bo'lsa — jim o'tmasin: Lab fermer FREE da qolib, dalalari monitoringdan chiqib qolardi
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM plans WHERE code = 'BUSINESS') THEN
        RAISE EXCEPTION 'V41: BUSINESS tarifi yo''q — Lab fermer obunasi yaratilmaydi';
    END IF;
END $$;

INSERT INTO farmer_subscriptions (farmer_id, plan_id, status, valid_from, valid_until, amount, currency, paid_at,
                                  payment_method, note, created_date, updated_date)
SELECT f.id, p.id, 'ACTIVE', CURRENT_DATE, DATE '2099-12-31', 0, p.currency, now(), 'OTHER',
       'Lab dalalari — tizim obunasi', now(), now()
  FROM farmers f JOIN plans p ON p.code = 'BUSINESS'
 WHERE f.lab = TRUE AND f.deleted = FALSE
   AND NOT EXISTS (SELECT 1 FROM farmer_subscriptions s WHERE s.farmer_id = f.id);

INSERT INTO permissions (name, description) VALUES
    ('field:write', 'Lab dalasini qo''shish va o''chirish (admin xaritada chizadi)')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'field:write'
 WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
