-- Nomlar rbac/Permissions.java va rbac/RoleNames.java bilan bir xil bo'lishi SHART
INSERT INTO permissions (name, description) VALUES
    ('user:read',   'Foydalanuvchilarni ko''rish'),
    ('user:create', 'Foydalanuvchi yaratish'),
    ('user:update', 'Foydalanuvchini yangilash'),
    ('user:delete', 'Foydalanuvchini o''chirish'),

    ('role:read',   'Rollarni ko''rish'),
    ('role:create', 'Rol yaratish'),
    ('role:update', 'Rolni yangilash'),
    ('role:delete', 'Rolni o''chirish'),
    ('role:assign', 'Rol biriktirish'),

    ('audit:read',  'Audit log''ni ko''rish')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO roles (name, description, system_role) VALUES
    ('ADMIN',    'Tizim admini',  TRUE),
    ('USER',     'Oddiy foydalanuvchi', TRUE),
    ('MANAGER',  'Menejer',       FALSE),
    ('OPERATOR', 'Operator',      FALSE)
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

-- ADMIN — hammasi
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('user:read', 'user:update', 'role:read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'OPERATOR'
  AND p.name IN ('user:read', 'user:update');

-- USER — hech qanday permission yo'q: faqat o'z ma'lumoti (/auth/me, /users/{o'z id})
