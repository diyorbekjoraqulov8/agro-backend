-- Brend: Agro → Dropy (2026-10-13). O'rnatilgan WELCOME matnida — admin o'zgartirgan matnda "Agro" bo'lmasa, tegilmaydi
UPDATE onboarding_steps
   SET texts = jsonb_build_object(
           'uz', replace(texts->>'uz', 'Men — Agro', 'Men — Dropy'),
           'ru', replace(texts->>'ru', 'Я — Agro', 'Я — Dropy'),
           'en', replace(texts->>'en', 'I am Agro', 'I am Dropy')),
       updated_date = now()
 WHERE code = 'WELCOME' AND (texts->>'uz' LIKE '%Men — Agro%' OR texts->>'ru' LIKE '%Я — Agro%' OR texts->>'en' LIKE '%I am Agro%');
