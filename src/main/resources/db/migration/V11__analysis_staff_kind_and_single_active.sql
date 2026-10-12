-- Xodim admin paneldan ishga tushirgan tahlil: fermerning bepul kvotasini (FREE) yemasin
ALTER TABLE analysis_jobs DROP CONSTRAINT ck_analysis_kind;
ALTER TABLE analysis_jobs ADD CONSTRAINT ck_analysis_kind CHECK (kind IN ('FREE', 'PAID', 'STAFF'));

-- Bitta dalada bir vaqtda faqat bitta faol tahlil. Service'dagi "exists" tekshiruvi ikki
-- parallel so'rovda ikkalasini ham o'tkazib yuboradi — kafolat faqat DB'da (409 CONFLICT)
CREATE UNIQUE INDEX uk_analysis_active_per_field ON analysis_jobs (field_id)
    WHERE status IN ('PENDING', 'RUNNING');
