-- C3 tuzatish: metadata kursori. Yangi surat dala ustida bulutli bo'lsa last_scene_date o'zgarmaydi — keyingi
-- tekshiruvda o'sha surat yana "yangi" bo'lib, og'ir hisob har kuni takrorlanardi. Endi since = GREATEST(ikkalasi).
ALTER TABLE field_monitoring ADD COLUMN last_seen_date DATE;
