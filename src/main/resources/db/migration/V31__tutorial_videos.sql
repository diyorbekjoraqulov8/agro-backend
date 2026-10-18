-- P4: yo'riqnoma videosi — har til uchun bitta (admin Onboarding sahifasida yuklaydi).
-- telegram_file_id — birinchi yuborishdan keyin Telegram bergan id: keyingi fermerlarga fayl qayta yuklanmaydi (tez)
CREATE TABLE tutorial_videos (
    language          VARCHAR(5)    NOT NULL,
    file_hash_id      VARCHAR(32)   NOT NULL,
    telegram_file_id  VARCHAR(255),
    updated_date      TIMESTAMP(6)  NOT NULL DEFAULT now(),
    updated_by_email  VARCHAR(120),

    CONSTRAINT pk_tutorial_videos PRIMARY KEY (language),
    CONSTRAINT fk_tutorial_videos_file FOREIGN KEY (file_hash_id) REFERENCES files (hash_id),
    CONSTRAINT ck_tutorial_videos_language CHECK (language IN ('uz', 'ru', 'en'))
);
