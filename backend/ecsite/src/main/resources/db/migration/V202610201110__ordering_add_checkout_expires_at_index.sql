-- 注文手続きの片付け（spec 017 AC-284、docs/backend/modules/order.md「注文手続き」）
-- 片付けのジョブが、期限が保存期間を過ぎた注文手続きを探すため
CREATE INDEX checkout_expires_at_idx ON ordering.checkout (expires_at);
