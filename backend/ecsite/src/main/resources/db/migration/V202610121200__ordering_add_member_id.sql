-- 注文手続きと注文を会員に結び付ける（docs/modules/order.md、spec 003 AC-125〜128）
-- 会員は会員モジュールの ID なので、外部キーは張らない（constitution 6）

-- 認証を入れる前にログインせずに作った注文・注文手続きと、その引当を消す（spec 003 の Non-goals。本番のデータはない）。
-- 引当の持ち主は注文手続き。明細と税率ごとの消費税額は、注文の削除で一緒に消える。
-- 在庫の調整履歴（注文番号の写しを持つ）とカートは残す
DELETE
FROM inventory.stock_reservation reservation
    USING ordering.checkout checkout
WHERE reservation.holder_id = checkout.id;
DELETE FROM ordering.sales_order;
DELETE FROM ordering.checkout;

ALTER TABLE ordering.checkout
    ADD COLUMN member_id uuid NOT NULL;

ALTER TABLE ordering.sales_order
    ADD COLUMN member_id uuid NOT NULL;

-- 自分の注文の一覧（注文日時の新しい順。AC-129）
CREATE INDEX sales_order_member_id_ordered_at_idx ON ordering.sales_order (member_id, ordered_at DESC, id);

COMMENT ON COLUMN ordering.checkout.member_id IS '会員 ID';
COMMENT ON COLUMN ordering.sales_order.member_id IS '会員 ID';
