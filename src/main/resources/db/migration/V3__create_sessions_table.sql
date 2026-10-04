CREATE TABLE sessions (
    id                          UUID         NOT NULL,
    user_id                     BIGINT       NOT NULL,
    device                      VARCHAR(255),
    ip                          VARCHAR(45),
    refresh_token_hash          VARCHAR(64),
    previous_refresh_token_hash VARCHAR(64),
    created_date                TIMESTAMP(6) NOT NULL,
    last_seen_at                TIMESTAMP(6),
    expires_at                  TIMESTAMP(6) NOT NULL,
    revoked_at                  TIMESTAMP(6),

    CONSTRAINT pk_sessions      PRIMARY KEY (id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_sessions_user_id ON sessions (user_id);

CREATE UNIQUE INDEX uk_sessions_refresh_hash
    ON sessions (refresh_token_hash) WHERE refresh_token_hash IS NOT NULL;

-- Qayta ishlatilgan (o'g'irlangan) refresh token'ni aniqlash uchun
CREATE INDEX idx_sessions_prev_refresh_hash
    ON sessions (previous_refresh_token_hash) WHERE previous_refresh_token_hash IS NOT NULL;
