-- 出荷（docs/design/modules/shipping.md）
-- 注文は他モジュールの ID なので、外部キーは張らない（バックエンドのアーキテクチャ 第 3 節）
CREATE SCHEMA IF NOT EXISTS shipping;

CREATE TABLE shipping.shipment
(
    id               uuid         NOT NULL,
    -- 1 つの注文に出荷は 1 つ
    order_id         uuid         NOT NULL,
    -- YAMATO / SAGAWA / JAPAN_POST
    carrier          varchar(20)  NOT NULL,
    -- ハイフンを除いた数字
    tracking_number  varchar(30)  NOT NULL,
    -- SHIPPED / IN_TRANSIT / DELIVERED / RETURNED
    status           varchar(20)  NOT NULL,
    shipped_at       timestamptz  NOT NULL,
    -- 配送状態を最後に進めた通知を反映した日時。まだ通知を反映していなければ null
    last_notified_at timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT shipment_pkey PRIMARY KEY (id),
    CONSTRAINT shipment_order_id_key UNIQUE (order_id),
    CONSTRAINT shipment_tracking_number_check CHECK (tracking_number ~ '^[0-9]+$')
);

-- 物流サービスの通知から、配送業者と送り状番号で出荷を探すため（一意にはしない）
CREATE INDEX shipment_carrier_tracking_number_idx ON shipping.shipment (carrier, tracking_number);
-- 返送の注文 ID の一覧を引くため
CREATE INDEX shipment_returned_idx ON shipping.shipment (order_id) WHERE status = 'RETURNED';

COMMENT ON TABLE shipping.shipment IS '出荷';
COMMENT ON COLUMN shipping.shipment.id IS '出荷 ID';
COMMENT ON COLUMN shipping.shipment.order_id IS '注文 ID';
COMMENT ON COLUMN shipping.shipment.carrier IS '配送業者';
COMMENT ON COLUMN shipping.shipment.tracking_number IS '送り状番号';
COMMENT ON COLUMN shipping.shipment.status IS '配送状態';
COMMENT ON COLUMN shipping.shipment.shipped_at IS '出荷日時';
COMMENT ON COLUMN shipping.shipment.last_notified_at IS '最後に通知を反映した日時';
COMMENT ON COLUMN shipping.shipment.created_at IS '作成日時';
COMMENT ON COLUMN shipping.shipment.updated_at IS '更新日時';
