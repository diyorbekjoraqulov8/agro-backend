-- Chegirmalar ro'yxatidagi "necha marta ishlatilgan" (GROUP BY discount_id) — FK uchun PostgreSQL index yaratmaydi
CREATE INDEX idx_subscriptions_discount ON farmer_subscriptions (discount_id) WHERE discount_id IS NOT NULL;
