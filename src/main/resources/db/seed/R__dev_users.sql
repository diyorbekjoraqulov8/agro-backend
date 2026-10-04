-- 🔴 FAQAT DEV. application-dev.yml da spring.flyway.locations orqali ulanadi,
-- prod profilida bu papka umuman o'qilmaydi.
--
-- Repeatable (R__): versiyalangan migratsiyalardan keyin ishlaydi, fayl o'zgarsa qayta
-- ishlaydi. Shuning uchun har bir INSERT idempotent bo'lishi SHART.
--
-- Parol hammasi uchun: Parol12345

INSERT INTO users (email, password) VALUES
    ('admin@app.co',    '{bcrypt}$2y$10$4XbuRhS2062QsL0obXhMZ.IoX9Bytt/7Z5titUkczDgkFIud6s5h2'),
    ('user@app.co',     '{bcrypt}$2y$10$4XbuRhS2062QsL0obXhMZ.IoX9Bytt/7Z5titUkczDgkFIud6s5h2'),
    ('manager@app.co',  '{bcrypt}$2y$10$4XbuRhS2062QsL0obXhMZ.IoX9Bytt/7Z5titUkczDgkFIud6s5h2'),
    ('operator@app.co', '{bcrypt}$2y$10$4XbuRhS2062QsL0obXhMZ.IoX9Bytt/7Z5titUkczDgkFIud6s5h2')
ON CONFLICT (email) WHERE deleted = FALSE DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = upper(split_part(u.email, '@', 1))
WHERE u.email IN ('admin@app.co', 'user@app.co', 'manager@app.co', 'operator@app.co')
  AND u.deleted = FALSE AND r.deleted = FALSE
ON CONFLICT DO NOTHING;
