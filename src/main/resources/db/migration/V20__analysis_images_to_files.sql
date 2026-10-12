-- Tahlil rasmlari (xarita, grafik) BYTEA o'rniga fayl servisida: analysis_images endi faqat hashId'larni saqlaydi.
-- Eski qatorlar ilova ishga tushganda fonda ko'chiriladi (AnalysisImageBackfill) va BYTEA bo'shatiladi.
-- Ko'chirish tugagach (barcha qatorda *_file_hash_id bor) keyingi migratsiyada map_png/chart_png o'chiriladi.
ALTER TABLE files
    DROP CONSTRAINT ck_files_source,
    ADD CONSTRAINT ck_files_source CHECK (source IN ('STAFF', 'TELEGRAM', 'SYSTEM'));   -- SYSTEM — tizim yasagan (tahlil xaritasi)

ALTER TABLE analysis_images
    ADD COLUMN map_file_hash_id   VARCHAR(32),
    ADD COLUMN chart_file_hash_id VARCHAR(32),
    ADD CONSTRAINT fk_analysis_images_map_file   FOREIGN KEY (map_file_hash_id)   REFERENCES files (hash_id),
    ADD CONSTRAINT fk_analysis_images_chart_file FOREIGN KEY (chart_file_hash_id) REFERENCES files (hash_id),
    ALTER COLUMN map_png DROP NOT NULL,
    ALTER COLUMN chart_png DROP NOT NULL,
    -- Har rasm yo faylda, yo (eski/zaxira) BYTEA'da bo'lsin
    ADD CONSTRAINT ck_analysis_images_map   CHECK (map_file_hash_id IS NOT NULL OR map_png IS NOT NULL),
    ADD CONSTRAINT ck_analysis_images_chart CHECK (chart_file_hash_id IS NOT NULL OR chart_png IS NOT NULL);

CREATE INDEX idx_analysis_images_legacy ON analysis_images (job_id) WHERE map_file_hash_id IS NULL;
