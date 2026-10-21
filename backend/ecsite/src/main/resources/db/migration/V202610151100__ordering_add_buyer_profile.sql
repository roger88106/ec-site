-- 注文時の年齢と性別（docs/backend/modules/order.md「注文時の年齢と性別」、spec 010 AC-212、222）
-- 注文の成立のときに、会員の token の生年月日と性別から求めて記録する。分からなければ空（不明）。
-- 列を足す前の注文は、すべて不明とする。

ALTER TABLE ordering.sales_order ADD COLUMN buyer_age smallint;
ALTER TABLE ordering.sales_order ADD COLUMN buyer_gender varchar(16);
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_buyer_age_check
    CHECK (buyer_age BETWEEN 0 AND 120);
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_buyer_gender_check
    CHECK (buyer_gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'));

COMMENT ON COLUMN ordering.sales_order.buyer_age IS '注文時の年齢';
COMMENT ON COLUMN ordering.sales_order.buyer_gender IS '注文時の性別';
