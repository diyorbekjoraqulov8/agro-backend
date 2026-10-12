-- Tahlil qaysi ekin bo'yicha qilingani — dala ekini keyin o'zgarsa (almashlab ekish:
-- g'alladan keyin mosh/poliz) eski hisobotlar buzilmasin. NULL — eski yozuvlar (dala ekini ishlatiladi)
ALTER TABLE analysis_jobs ADD COLUMN crop VARCHAR(32);

UPDATE analysis_jobs j SET crop = f.crop FROM fields f WHERE f.id = j.field_id AND j.crop IS NULL;
