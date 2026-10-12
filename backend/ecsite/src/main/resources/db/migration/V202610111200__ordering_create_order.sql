-- 注文手続き、注文、注文明細、注文の税率ごとの消費税額（docs/modules/order.md、spec 002 US-16〜21）
-- schema は ordering（order は SQL の予約語のため）
-- カート・バリアントは他モジュールの ID なので、外部キーは張らない（constitution 6）
CREATE SCHEMA IF NOT EXISTS ordering;

-- 注文手続き。引当の持ち主になる
CREATE TABLE ordering.checkout
(
    id           uuid        NOT NULL,
    cart_id      uuid        NOT NULL,
    -- 注文手続きを始めたときのカートのバージョン。注文の時点で違えば、カートが変わっている
    cart_version bigint      NOT NULL,
    expires_at   timestamptz NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT checkout_pkey PRIMARY KEY (id)
);

-- カートの変更で、そのカートの注文手続きの引当を解くため
CREATE INDEX checkout_cart_id_idx ON ordering.checkout (cart_id);

-- 注文
CREATE TABLE ordering.sales_order
(
    id                          uuid         NOT NULL,
    -- 注文番号。注文日（日本時間）＋ランダムな数字 6 桁（BR-10）
    order_number                varchar(15)  NOT NULL,
    checkout_id                 uuid         NOT NULL,
    ordered_at                  timestamptz  NOT NULL,
    -- ACCEPTED / CANCELLATION_REQUESTED / CANCELLED（BR-9）
    status                      varchar(30)  NOT NULL,
    items_total                 bigint       NOT NULL,
    shipping_fee                bigint       NOT NULL,
    total_amount                bigint       NOT NULL,
    email                       varchar(254) NOT NULL,
    -- お届け先
    recipient_name              varchar(50)  NOT NULL,
    postal_code                 char(7)      NOT NULL,
    prefecture                  varchar(4)   NOT NULL,
    address                     varchar(200) NOT NULL,
    phone                       varchar(11)  NOT NULL,
    -- キャンセルの申請（1 つの注文に 1 回だけ）。申請がなければ null
    cancellation_requested_at   timestamptz,
    cancellation_request_reason varchar(500),
    -- PENDING / APPROVED / REJECTED
    cancellation_request_result varchar(20),
    cancellation_decided_at     timestamptz,
    -- キャンセル。キャンセルでなければ null
    cancelled_at                timestamptz,
    cancelled_by_operator       boolean,
    -- 運営者の判断によるキャンセルの理由（AC-103）
    cancellation_reason         varchar(500),
    version                     bigint       NOT NULL,
    created_at                  timestamptz  NOT NULL,
    updated_at                  timestamptz  NOT NULL,
    CONSTRAINT sales_order_pkey PRIMARY KEY (id),
    CONSTRAINT sales_order_order_number_key UNIQUE (order_number),
    -- 注文手続き 1 つにつき注文は 1 つ（AC-91）
    CONSTRAINT sales_order_checkout_id_key UNIQUE (checkout_id),
    CONSTRAINT sales_order_status_check CHECK (status IN ('ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED')),
    CONSTRAINT sales_order_cancellation_request_result_check
        CHECK (cancellation_request_result IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT sales_order_amounts_check CHECK (items_total >= 0 AND shipping_fee >= 0 AND total_amount = items_total + shipping_fee)
);

-- B 側の一覧（注文日時の新しい順、状態での絞り込み。AC-97、98）
CREATE INDEX sales_order_ordered_at_idx ON ordering.sales_order (ordered_at DESC, id);
CREATE INDEX sales_order_status_ordered_at_idx ON ordering.sales_order (status, ordered_at DESC);

-- 注文明細。注文時点の写し（AC-92）
CREATE TABLE ordering.sales_order_line
(
    order_id            uuid          NOT NULL,
    -- 明細の並び（1 始まり。カートの並び）
    line_no             smallint      NOT NULL,
    -- 在庫の戻しに使う。表示には写しを使う
    variant_id          uuid          NOT NULL,
    product_code        varchar(32)   NOT NULL,
    variant_code        varchar(32)   NOT NULL,
    product_name        varchar(200)  NOT NULL,
    -- 軸の名前と値の写し（[{"name": "色", "value": "白"}, ...]）
    axis_values         jsonb         NOT NULL,
    unit_price_excl_tax bigint        NOT NULL,
    rate_percent        numeric(4, 2) NOT NULL,
    unit_price_incl_tax bigint        NOT NULL,
    quantity            int           NOT NULL,
    subtotal            bigint        NOT NULL,
    CONSTRAINT sales_order_line_pkey PRIMARY KEY (order_id, line_no),
    CONSTRAINT sales_order_line_order_id_fkey FOREIGN KEY (order_id) REFERENCES ordering.sales_order (id) ON DELETE CASCADE,
    CONSTRAINT sales_order_line_quantity_check CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT sales_order_line_subtotal_check CHECK (subtotal = unit_price_incl_tax * quantity)
);

-- 注文の税率ごとの消費税額（BR-8）
CREATE TABLE ordering.sales_order_tax
(
    order_id       uuid          NOT NULL,
    rate_percent   numeric(4, 2) NOT NULL,
    -- その税率の対象の税込金額（小計と送料）の合計
    taxable_amount bigint        NOT NULL,
    tax_amount     bigint        NOT NULL,
    CONSTRAINT sales_order_tax_pkey PRIMARY KEY (order_id, rate_percent),
    CONSTRAINT sales_order_tax_order_id_fkey FOREIGN KEY (order_id) REFERENCES ordering.sales_order (id) ON DELETE CASCADE
);

COMMENT ON TABLE ordering.checkout IS '注文手続き';
COMMENT ON COLUMN ordering.checkout.id IS '注文手続き ID';
COMMENT ON COLUMN ordering.checkout.cart_id IS 'カート ID';
COMMENT ON COLUMN ordering.checkout.cart_version IS 'カートのバージョン';
COMMENT ON COLUMN ordering.checkout.expires_at IS '引当の期限';
COMMENT ON COLUMN ordering.checkout.created_at IS '作成日時';

COMMENT ON TABLE ordering.sales_order IS '注文';
COMMENT ON COLUMN ordering.sales_order.id IS '注文 ID';
COMMENT ON COLUMN ordering.sales_order.order_number IS '注文番号';
COMMENT ON COLUMN ordering.sales_order.checkout_id IS '注文手続き ID';
COMMENT ON COLUMN ordering.sales_order.ordered_at IS '注文日時';
COMMENT ON COLUMN ordering.sales_order.status IS '注文の状態';
COMMENT ON COLUMN ordering.sales_order.items_total IS '商品の合計';
COMMENT ON COLUMN ordering.sales_order.shipping_fee IS '送料';
COMMENT ON COLUMN ordering.sales_order.total_amount IS '総額';
COMMENT ON COLUMN ordering.sales_order.email IS 'メールアドレス';
COMMENT ON COLUMN ordering.sales_order.recipient_name IS '氏名';
COMMENT ON COLUMN ordering.sales_order.postal_code IS '郵便番号';
COMMENT ON COLUMN ordering.sales_order.prefecture IS '都道府県';
COMMENT ON COLUMN ordering.sales_order.address IS '住所';
COMMENT ON COLUMN ordering.sales_order.phone IS '電話番号';
COMMENT ON COLUMN ordering.sales_order.cancellation_requested_at IS 'キャンセルの申請日時';
COMMENT ON COLUMN ordering.sales_order.cancellation_request_reason IS 'キャンセルの申請の理由';
COMMENT ON COLUMN ordering.sales_order.cancellation_request_result IS 'キャンセルの申請の結果';
COMMENT ON COLUMN ordering.sales_order.cancellation_decided_at IS 'キャンセルの申請の判断日時';
COMMENT ON COLUMN ordering.sales_order.cancelled_at IS 'キャンセル日時';
COMMENT ON COLUMN ordering.sales_order.cancelled_by_operator IS '運営者のキャンセル';
COMMENT ON COLUMN ordering.sales_order.cancellation_reason IS '運営者のキャンセルの理由';
COMMENT ON COLUMN ordering.sales_order.version IS 'バージョン';
COMMENT ON COLUMN ordering.sales_order.created_at IS '作成日時';
COMMENT ON COLUMN ordering.sales_order.updated_at IS '更新日時';

COMMENT ON TABLE ordering.sales_order_line IS '注文明細';
COMMENT ON COLUMN ordering.sales_order_line.order_id IS '注文 ID';
COMMENT ON COLUMN ordering.sales_order_line.line_no IS '行番号';
COMMENT ON COLUMN ordering.sales_order_line.variant_id IS 'バリアント ID';
COMMENT ON COLUMN ordering.sales_order_line.product_code IS '商品品番';
COMMENT ON COLUMN ordering.sales_order_line.variant_code IS 'バリアント品番';
COMMENT ON COLUMN ordering.sales_order_line.product_name IS '商品名';
COMMENT ON COLUMN ordering.sales_order_line.axis_values IS '軸の値';
COMMENT ON COLUMN ordering.sales_order_line.unit_price_excl_tax IS '税抜単価';
COMMENT ON COLUMN ordering.sales_order_line.rate_percent IS '率';
COMMENT ON COLUMN ordering.sales_order_line.unit_price_incl_tax IS '税込単価';
COMMENT ON COLUMN ordering.sales_order_line.quantity IS '数量';
COMMENT ON COLUMN ordering.sales_order_line.subtotal IS '小計';

COMMENT ON TABLE ordering.sales_order_tax IS '注文の税率ごとの消費税額';
COMMENT ON COLUMN ordering.sales_order_tax.order_id IS '注文 ID';
COMMENT ON COLUMN ordering.sales_order_tax.rate_percent IS '率';
COMMENT ON COLUMN ordering.sales_order_tax.taxable_amount IS '対象額';
COMMENT ON COLUMN ordering.sales_order_tax.tax_amount IS '消費税額';
