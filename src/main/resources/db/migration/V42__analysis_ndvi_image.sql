-- L1b: tahlil debug bloki — mavsum cho'qqisi NDVI xaritasi (fayl servisida). Qolgan debug — analysis_jobs.result jsonb
ALTER TABLE analysis_images
    ADD COLUMN ndvi_file_hash_id VARCHAR(32),
    ADD CONSTRAINT fk_analysis_images_ndvi_file FOREIGN KEY (ndvi_file_hash_id) REFERENCES files (hash_id);
