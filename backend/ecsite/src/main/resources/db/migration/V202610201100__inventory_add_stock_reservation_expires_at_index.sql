-- 引当の片付け（spec 017 AC-284、docs/design/modules/inventory.md「引当」）
-- 片付けのジョブが、期限が保存期間を過ぎた引当を探すため
CREATE INDEX stock_reservation_expires_at_idx ON inventory.stock_reservation (expires_at);
