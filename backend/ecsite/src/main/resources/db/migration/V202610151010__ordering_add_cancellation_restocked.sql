-- キャンセルで在庫に戻したか（docs/design/modules/order.md「状態遷移」、spec 009 AC-191）
-- 返送された注文のキャンセルでは、運営者が在庫に戻すかを選ぶ。それより前のキャンセルは、すべて在庫に戻している。

ALTER TABLE ordering.sales_order ADD COLUMN cancellation_restocked boolean;
UPDATE ordering.sales_order SET cancellation_restocked = true WHERE cancelled_at IS NOT NULL;
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_cancellation_restocked_check
    CHECK ((cancelled_at IS NULL) = (cancellation_restocked IS NULL));

COMMENT ON COLUMN ordering.sales_order.cancellation_restocked IS 'キャンセルで在庫に戻したか';
