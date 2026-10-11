-- Bot holati DB'da: restart'da fermer savol o'rtasida qolib ketmasin
CREATE TABLE bot_states (
    telegram_user_id BIGINT       NOT NULL,
    step             VARCHAR(32)  NOT NULL,
    data             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT now(),

    CONSTRAINT pk_bot_states PRIMARY KEY (telegram_user_id)
);
