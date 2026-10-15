-- レポートの写しと状態の履歴（docs/backend/modules/reporting.md「集めるデータ」、spec 010 AC-223〜225）
-- 注文・バリアント・商品・会員は他モジュールの ID なので、外部キーは張らない（バックエンドのアーキテクチャ 第 3 節）
-- 写しはイベントを受けるたびに注文の API から読み直して上書きする。状態によらず、すべての注文を持つ。
CREATE SCHEMA IF NOT EXISTS reporting;

CREATE TABLE reporting.sales_order
(
    order_id          uuid        NOT NULL,
    order_number      varchar(15) NOT NULL,
    member_id         uuid        NOT NULL,
    ordered_at        timestamptz NOT NULL,
    -- 注文日（日本時間）。日ごとの集計に使う
    ordered_on        date        NOT NULL,
    status            varchar(30) NOT NULL,
    shipped_at        timestamptz,
    -- 出荷日（日本時間）
    shipped_on        date,
    delivery_status   varchar(20),
    cancelled_at      timestamptz,
    cancellation_kind varchar(30),
    restocked         boolean,
    postal_code       char(7)     NOT NULL,
    -- 全国地方公共団体コードの上 2 桁。読み替えられなければ空
    prefecture_code   char(2),
    buyer_age         smallint,
    buyer_gender      varchar(16),
    total_amount      bigint      NOT NULL,
    shipping_fee      bigint      NOT NULL,
    tax_total         bigint      NOT NULL,
    shipping_tax      bigint      NOT NULL,
    refreshed_at      timestamptz NOT NULL,
    CONSTRAINT sales_order_pkey PRIMARY KEY (order_id),
    CONSTRAINT sales_order_status_check
        CHECK (status IN ('PENDING_PAYMENT', 'ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED', 'SHIPPED')),
    CONSTRAINT sales_order_delivery_status_check
        CHECK (delivery_status IN ('SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'RETURNED')),
    CONSTRAINT sales_order_shipped_check CHECK ((shipped_at IS NULL) = (shipped_on IS NULL))
);

CREATE INDEX sales_order_ordered_on_idx ON reporting.sales_order (ordered_on);
CREATE INDEX sales_order_shipped_on_idx ON reporting.sales_order (shipped_on) WHERE shipped_on IS NOT NULL;

CREATE TABLE reporting.sales_order_line
(
    order_id          uuid         NOT NULL,
    line_no           smallint     NOT NULL,
    -- 商品カタログで求めた商品 ID。求められなければ空
    product_id        uuid,
    variant_id        uuid         NOT NULL,
    product_code      varchar(32)  NOT NULL,
    variant_code      varchar(32)  NOT NULL,
    product_name      varchar(200) NOT NULL,
    axis_values       jsonb        NOT NULL,
    quantity          integer      NOT NULL,
    rate_percent      numeric(4, 2) NOT NULL,
    subtotal_incl_tax bigint       NOT NULL,
    subtotal_excl_tax bigint       NOT NULL,
    CONSTRAINT sales_order_line_pkey PRIMARY KEY (order_id, line_no),
    CONSTRAINT sales_order_line_order_id_fkey FOREIGN KEY (order_id)
        REFERENCES reporting.sales_order (order_id) ON DELETE CASCADE
);

CREATE INDEX sales_order_line_product_id_idx ON reporting.sales_order_line (product_id);

CREATE TABLE reporting.status_history
(
    -- 元のイベント ID。同じイベントを重ねて足さない
    event_id          uuid        NOT NULL,
    order_id          uuid        NOT NULL,
    -- ORDER_STATUS（注文の状態の変化）、DELIVERY_STATUS（配送状態の変化）
    kind              varchar(20) NOT NULL,
    from_status       varchar(30),
    to_status         varchar(30) NOT NULL,
    occurred_at       timestamptz NOT NULL,
    cancellation_kind varchar(30),
    restocked         boolean,
    recorded_at       timestamptz NOT NULL,
    CONSTRAINT status_history_pkey PRIMARY KEY (event_id),
    CONSTRAINT status_history_kind_check CHECK (kind IN ('ORDER_STATUS', 'DELIVERY_STATUS'))
);

CREATE INDEX status_history_order_id_idx ON reporting.status_history (order_id, occurred_at);

COMMENT ON TABLE reporting.sales_order IS '注文の写し（レポート）';
COMMENT ON COLUMN reporting.sales_order.order_id IS '注文 ID';
COMMENT ON COLUMN reporting.sales_order.order_number IS '注文番号';
COMMENT ON COLUMN reporting.sales_order.member_id IS '会員 ID';
COMMENT ON COLUMN reporting.sales_order.ordered_at IS '注文日時';
COMMENT ON COLUMN reporting.sales_order.ordered_on IS '注文日';
COMMENT ON COLUMN reporting.sales_order.status IS '注文の状態';
COMMENT ON COLUMN reporting.sales_order.shipped_at IS '出荷日時';
COMMENT ON COLUMN reporting.sales_order.shipped_on IS '出荷日';
COMMENT ON COLUMN reporting.sales_order.delivery_status IS '配送状態';
COMMENT ON COLUMN reporting.sales_order.cancelled_at IS 'キャンセル日時';
COMMENT ON COLUMN reporting.sales_order.cancellation_kind IS 'キャンセルの種類';
COMMENT ON COLUMN reporting.sales_order.restocked IS 'キャンセルで在庫に戻したか';
COMMENT ON COLUMN reporting.sales_order.postal_code IS '郵便番号';
COMMENT ON COLUMN reporting.sales_order.prefecture_code IS '全国地方公共団体コード';
COMMENT ON COLUMN reporting.sales_order.buyer_age IS '注文時の年齢';
COMMENT ON COLUMN reporting.sales_order.buyer_gender IS '注文時の性別';
COMMENT ON COLUMN reporting.sales_order.total_amount IS '総額';
COMMENT ON COLUMN reporting.sales_order.shipping_fee IS '送料';
COMMENT ON COLUMN reporting.sales_order.tax_total IS '消費税の合計';
COMMENT ON COLUMN reporting.sales_order.shipping_tax IS '送料の消費税';
COMMENT ON COLUMN reporting.sales_order.refreshed_at IS '写しの更新日時';

COMMENT ON TABLE reporting.sales_order_line IS '注文明細の写し（レポート）';
COMMENT ON COLUMN reporting.sales_order_line.order_id IS '注文 ID';
COMMENT ON COLUMN reporting.sales_order_line.line_no IS '行番号';
COMMENT ON COLUMN reporting.sales_order_line.product_id IS '商品 ID';
COMMENT ON COLUMN reporting.sales_order_line.variant_id IS 'バリアント ID';
COMMENT ON COLUMN reporting.sales_order_line.product_code IS '商品品番';
COMMENT ON COLUMN reporting.sales_order_line.variant_code IS 'バリアント品番';
COMMENT ON COLUMN reporting.sales_order_line.product_name IS '商品名';
COMMENT ON COLUMN reporting.sales_order_line.axis_values IS '軸の値';
COMMENT ON COLUMN reporting.sales_order_line.quantity IS '数量';
COMMENT ON COLUMN reporting.sales_order_line.rate_percent IS '税率';
COMMENT ON COLUMN reporting.sales_order_line.subtotal_incl_tax IS '税込小計';
COMMENT ON COLUMN reporting.sales_order_line.subtotal_excl_tax IS '税抜小計';

COMMENT ON TABLE reporting.status_history IS '状態の履歴';
COMMENT ON COLUMN reporting.status_history.event_id IS 'イベント ID';
COMMENT ON COLUMN reporting.status_history.order_id IS '注文 ID';
COMMENT ON COLUMN reporting.status_history.kind IS '出来事';
COMMENT ON COLUMN reporting.status_history.from_status IS '前の状態';
COMMENT ON COLUMN reporting.status_history.to_status IS '後の状態';
COMMENT ON COLUMN reporting.status_history.occurred_at IS '発生日時';
COMMENT ON COLUMN reporting.status_history.cancellation_kind IS 'キャンセルの種類';
COMMENT ON COLUMN reporting.status_history.restocked IS 'キャンセルで在庫に戻したか';
COMMENT ON COLUMN reporting.status_history.recorded_at IS '記録日時';
