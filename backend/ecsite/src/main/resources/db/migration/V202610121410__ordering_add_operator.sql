-- 注文に、キャンセルの申請を判断した運営者と、キャンセルした運営者の ID と氏名の写しを足す
-- （docs/modules/order.md、spec 003 AC-142）。却下の後に運営者がキャンセルすることがあるので、2 組を別々に持つ。
-- 運営者は IdP の運営者なので、外部キーは張らない。既存の行は空
ALTER TABLE ordering.sales_order
    ADD COLUMN cancellation_decided_by_id   uuid,
    ADD COLUMN cancellation_decided_by_name varchar(255),
    ADD COLUMN cancelled_by_id              uuid,
    ADD COLUMN cancelled_by_name            varchar(255);

COMMENT ON COLUMN ordering.sales_order.cancellation_decided_by_id IS 'キャンセルの申請を判断した運営者 ID';
COMMENT ON COLUMN ordering.sales_order.cancellation_decided_by_name IS 'キャンセルの申請を判断した運営者の氏名（写し）';
COMMENT ON COLUMN ordering.sales_order.cancelled_by_id IS 'キャンセルした運営者 ID';
COMMENT ON COLUMN ordering.sales_order.cancelled_by_name IS 'キャンセルした運営者の氏名（写し）';
