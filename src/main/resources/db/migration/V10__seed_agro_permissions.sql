-- Nomlar rbac/Permissions.java va rbac/RoleNames.java bilan bir xil bo'lishi SHART
INSERT INTO permissions (name, description) VALUES
    ('farmer:read',   'Fermerlarni ko''rish'),
    ('farmer:update', 'Fermer va lead holatini o''zgartirish'),
    ('field:read',    'Dalalarni ko''rish'),
    ('analysis:read', 'Tahlil natijalarini ko''rish'),
    ('analysis:run',  'Tahlilni qayta ishga tushirish')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO roles (name, description, system_role) VALUES
    ('AGRONOMIST', 'Agronom: fermer, dala, tahlillarni ko''radi', FALSE)
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

-- ADMIN — V5 dagi CROSS JOIN faqat o'sha paytdagi huquqlarni bergan, yangilarini alohida
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('farmer:read', 'farmer:update', 'field:read', 'analysis:read', 'analysis:run');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'AGRONOMIST'
  AND p.name IN ('farmer:read', 'farmer:update', 'field:read', 'analysis:read');
