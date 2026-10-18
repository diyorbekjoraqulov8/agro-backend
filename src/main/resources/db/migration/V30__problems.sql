-- P2: "Muammolar" — fermer/xizmat xatolari bir joyda. Bir xil xato (manba + fermer + matn izi) ochiq turganda
-- yangi qator emas, occurrences oshadi (ProblemWriter upsert). Hal qilingach (resolved_at) — keyingisi yangi qator.
CREATE TABLE problems (
    id                 BIGSERIAL     NOT NULL,
    source             VARCHAR(20)   NOT NULL,
    level              VARCHAR(10)   NOT NULL,
    farmer_id          BIGINT,
    farmer_key         BIGINT        NOT NULL,          -- farmer_id yoki 0 (unique index NULL'ni teng ko'rmaydi)
    field_id           BIGINT,
    ref_type           VARCHAR(30),
    ref_id             BIGINT,
    fingerprint        VARCHAR(64)   NOT NULL,          -- md5(manba + raqamlarsiz matn)
    message            VARCHAR(500)  NOT NULL,
    detail             TEXT,
    occurrences        INTEGER       NOT NULL DEFAULT 1,
    first_seen         TIMESTAMP(6)  NOT NULL DEFAULT now(),
    last_seen          TIMESTAMP(6)  NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMP(6),
    resolved_by_email  VARCHAR(120),

    CONSTRAINT pk_problems PRIMARY KEY (id),
    CONSTRAINT ck_problems_source CHECK (source IN ('BOT', 'ANALYSIS', 'MONITORING', 'ADVISOR', 'NOTIFICATION', 'WEATHER', 'IRRIGATION', 'API', 'SYSTEM')),
    CONSTRAINT ck_problems_level  CHECK (level IN ('ERROR', 'WARN'))
);
-- FK yo'q (audit kabi): fermer o'chirilsa ham tarix qoladi
CREATE UNIQUE INDEX ux_problems_open ON problems (source, farmer_key, fingerprint) WHERE resolved_at IS NULL;
CREATE INDEX idx_problems_last_seen ON problems (last_seen DESC);
CREATE INDEX idx_problems_farmer ON problems (farmer_id, last_seen DESC) WHERE farmer_id IS NOT NULL;

INSERT INTO permissions (name, description) VALUES
    ('problems:read',   'Muammolar (xatolar) va xizmatlar holati — ko''rish'),
    ('problems:manage', 'Muammoni "hal qilindi" deb belgilash')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'problems:read'
 WHERE r.name IN ('ADMIN', 'MANAGER', 'OPERATOR')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'problems:manage'
 WHERE r.name IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
