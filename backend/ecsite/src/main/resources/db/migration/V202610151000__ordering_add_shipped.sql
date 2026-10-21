-- 出荷済み（docs/backend/modules/order.md「状態遷移」、spec 009 BR-18）
-- 受付の注文は、売上確定の後に出荷済みになる。出荷の内容（配送業者、送り状番号、配送状態）は出荷モジュールが持つ。

ALTER TABLE ordering.sales_order DROP CONSTRAINT sales_order_status_check;
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED', 'SHIPPED'));
