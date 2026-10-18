-- Support bot: har fermerga xodimlar guruhida bitta mavzu (forum topic) — barcha murojaatlari shu yerda.
-- support_chat_id — qaysi guruhdagi mavzu: SUPPORT_GROUP_ID almashsa eski mavzu raqami boshqa fermerga tushib qolmasin
ALTER TABLE farmers
    ADD COLUMN support_chat_id  BIGINT,
    ADD COLUMN support_topic_id BIGINT;
CREATE UNIQUE INDEX uk_farmers_support_topic ON farmers (support_chat_id, support_topic_id) WHERE support_topic_id IS NOT NULL;

-- Xodimning Telegram ID'si — guruhdagi javobi shu xodim nomidan. Faqat support bot orqali bog'lanadi
-- (admin paneldagi bir martalik havola → botda /start link_<kod>): egalik isbotlanadi
ALTER TABLE users ADD COLUMN telegram_user_id BIGINT;
CREATE UNIQUE INDEX uk_users_telegram ON users (telegram_user_id) WHERE telegram_user_id IS NOT NULL AND deleted = FALSE;
