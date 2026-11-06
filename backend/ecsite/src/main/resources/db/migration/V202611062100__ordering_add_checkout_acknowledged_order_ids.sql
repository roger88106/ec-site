-- 注文手続きの確認した注文（注文モジュールの「支払待ちの注文の確認」）
-- 注文手続きを始めるときに、会員が確認した支払待ちの注文の ID の一覧。変わらない一覧なので JSON で持つ。
-- 列を追加する前の注文手続きは、確認していないものとして空の配列にする。既存の注文は変えない。

ALTER TABLE ordering.checkout ADD COLUMN acknowledged_order_ids jsonb NOT NULL DEFAULT '[]';

COMMENT ON COLUMN ordering.checkout.acknowledged_order_ids IS '確認した注文の ID の一覧（確認していなければ空の配列）';
