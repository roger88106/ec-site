-- 支払待ちと、キャンセルの種類（docs/backend/modules/order.md、spec 007 BR-14、AC-155）
-- 注文は「支払待ち」で作られ、与信の成功で「受付」になる。
-- キャンセルの「運営者の判断か」の真偽値では支払期限切れを表せないので、種類に置き換える。

ALTER TABLE ordering.sales_order DROP CONSTRAINT sales_order_status_check;
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED'));

-- REQUEST_APPROVED / OPERATOR / PAYMENT_EXPIRED。キャンセルでなければ null
ALTER TABLE ordering.sales_order ADD COLUMN cancellation_kind varchar(20);
UPDATE ordering.sales_order
SET cancellation_kind = CASE WHEN cancelled_by_operator THEN 'OPERATOR' ELSE 'REQUEST_APPROVED' END
WHERE cancelled_at IS NOT NULL;
ALTER TABLE ordering.sales_order DROP COLUMN cancelled_by_operator;
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_cancellation_kind_check
    CHECK (cancellation_kind IN ('REQUEST_APPROVED', 'OPERATOR', 'PAYMENT_EXPIRED'));
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_cancellation_check
    CHECK ((cancelled_at IS NULL) = (cancellation_kind IS NULL));

COMMENT ON COLUMN ordering.sales_order.cancellation_kind IS 'キャンセルの種類';
