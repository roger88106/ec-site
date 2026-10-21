-- 在庫の調整履歴に、調整した運営者の氏名の写しを足す（docs/modules/inventory.md、spec 003 AC-142）
-- 運営者 ID は既存の actor_id に入れる。どちらも、運営者の調整だけに入り、注文に由来する調整と既存の行は空
ALTER TABLE inventory.stock_adjustment
    ADD COLUMN actor_name varchar(255);

COMMENT ON COLUMN inventory.stock_adjustment.actor_name IS '操作者の氏名（写し）';
