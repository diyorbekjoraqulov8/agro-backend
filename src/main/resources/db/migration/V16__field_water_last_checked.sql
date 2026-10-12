-- Eslatma qarori shu (dala mahalliy) kunda qabul qilinganmi — yuborilgan yoki yuboradigan narsa yo'q.
-- Job "soat 7 da" emas, "soat 7 dan keyin, bugun hali tekshirilmagan" dalalarni oladi:
-- aylanish kech tugasa ham dala tushib qolmaydi, Python esa kuniga bir marta chaqiriladi.
ALTER TABLE field_water ADD COLUMN last_checked_on DATE;
UPDATE field_water SET last_checked_on = last_reminder_on;
