-- 引当と、注文による在庫の増減（docs/modules/inventory.md「引当」、spec 002 BR-11、AC-107）
-- 持ち主（注文手続き）と注文は注文モジュールの ID なので、外部キーは張らない（constitution 6）

-- 引当。期限切れは数えないだけで、消さない（削除は定期ジョブの方式が決まってから）
CREATE TABLE inventory.stock_reservation
(
    id            uuid        NOT NULL,
    -- 持ち主（注文手続き）
    holder_id     uuid        NOT NULL,
    stock_item_id uuid        NOT NULL,
    quantity      int         NOT NULL,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL,
    CONSTRAINT stock_reservation_pkey PRIMARY KEY (id),
    -- 在庫を消したら、その引当も消す
    CONSTRAINT stock_reservation_stock_item_id_fkey FOREIGN KEY (stock_item_id) REFERENCES inventory.stock_item (id) ON DELETE CASCADE,
    -- 持ち主と在庫の組で 1 つ
    CONSTRAINT stock_reservation_holder_id_stock_item_id_key UNIQUE (holder_id, stock_item_id),
    CONSTRAINT stock_reservation_quantity_check CHECK (quantity > 0)
);

-- 在庫ごとに有効な引当の合計を出すため
CREATE INDEX stock_reservation_stock_item_id_expires_at_idx ON inventory.stock_reservation (stock_item_id, expires_at);

COMMENT ON TABLE inventory.stock_reservation IS '引当';
COMMENT ON COLUMN inventory.stock_reservation.id IS '引当 ID';
COMMENT ON COLUMN inventory.stock_reservation.holder_id IS '持ち主 ID';
COMMENT ON COLUMN inventory.stock_reservation.stock_item_id IS '在庫 ID';
COMMENT ON COLUMN inventory.stock_reservation.quantity IS '数量';
COMMENT ON COLUMN inventory.stock_reservation.expires_at IS '期限';
COMMENT ON COLUMN inventory.stock_reservation.created_at IS '作成日時';

-- 在庫調整：注文に由来する理由と、その注文（AC-107）
ALTER TABLE inventory.stock_adjustment
    DROP CONSTRAINT stock_adjustment_reason_check,
    ADD CONSTRAINT stock_adjustment_reason_check
        CHECK (reason IN ('ARRIVAL', 'STOCKTAKE', 'DAMAGE_OR_LOSS', 'OTHER', 'ORDER', 'ORDER_CANCELLATION')),
    ADD COLUMN order_id     uuid,
    -- 注文番号の写し（constitution 7.1）
    ADD COLUMN order_number varchar(15),
    -- 注文の理由のときだけ、注文を持つ
    ADD CONSTRAINT stock_adjustment_order_check
        CHECK ((reason IN ('ORDER', 'ORDER_CANCELLATION')) = (order_id IS NOT NULL AND order_number IS NOT NULL));

COMMENT ON COLUMN inventory.stock_adjustment.order_id IS '注文 ID';
COMMENT ON COLUMN inventory.stock_adjustment.order_number IS '注文番号';
