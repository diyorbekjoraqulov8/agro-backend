-- Admin panel → "Fayllar": barcha fayllarni ko'rish (qaysi suhbatda ekanidan qat'i nazar).
-- file:read operatorlarda ham bor, lekin u faqat hashId'ni bilganda (o'z suhbatida) ishlaydi; ro'yxat — alohida huquq.
INSERT INTO permissions (name, description) VALUES
    ('file:manage', 'Barcha fayllar ro''yxati va statistikasi')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'file:manage'
ON CONFLICT DO NOTHING;

CREATE INDEX idx_files_created ON files (created_date DESC);
