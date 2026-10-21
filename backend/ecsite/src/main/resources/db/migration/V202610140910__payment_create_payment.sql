-- 支払（docs/backend/modules/payment.md、spec 007 BR-15、16）
-- 注文は他モジュールの ID なので、外部キーは張らない（バックエンドのアーキテクチャ 第 3 節）
CREATE SCHEMA IF NOT EXISTS payment;

CREATE TABLE payment.payment
(
    id                uuid         NOT NULL,
    -- 1 つの注文に支払は 1 つ
    order_id          uuid         NOT NULL,
    -- 注文の総額（円）
    amount            bigint       NOT NULL,
    deadline          timestamptz  NOT NULL,
    -- UNPAID / AUTHORIZED / VOID_PENDING / VOIDED / NOT_PAID
    status            varchar(20)  NOT NULL,
    -- 開いている支払の画面（決済サービスでの ID と URL）。まだ開いていなければ null
    page_id           varchar(255),
    page_url          varchar(2048),
    -- 決済サービスでの与信の ID。与信がなければ null
    authorization_ref varchar(255),
    version           bigint       NOT NULL,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT payment_pkey PRIMARY KEY (id),
    CONSTRAINT payment_order_id_key UNIQUE (order_id),
    CONSTRAINT payment_amount_check CHECK (amount >= 0),
    CONSTRAINT payment_page_check CHECK ((page_id IS NULL) = (page_url IS NULL))
);

-- 期限切れのジョブが、期限を過ぎた未払いの支払を探すため
CREATE INDEX payment_unpaid_deadline_idx ON payment.payment (deadline) WHERE status = 'UNPAID';
-- 通知（支払の画面の ID で届く）から支払を探すため
CREATE UNIQUE INDEX payment_page_id_key ON payment.payment (page_id);

COMMENT ON TABLE payment.payment IS '支払';
COMMENT ON COLUMN payment.payment.id IS '支払 ID';
COMMENT ON COLUMN payment.payment.order_id IS '注文 ID';
COMMENT ON COLUMN payment.payment.amount IS '金額';
COMMENT ON COLUMN payment.payment.deadline IS '支払の期限';
COMMENT ON COLUMN payment.payment.status IS '支払の状態';
COMMENT ON COLUMN payment.payment.page_id IS '支払の画面 ID';
COMMENT ON COLUMN payment.payment.page_url IS '支払の画面の URL';
COMMENT ON COLUMN payment.payment.authorization_ref IS '与信の参照';
COMMENT ON COLUMN payment.payment.version IS 'バージョン';
COMMENT ON COLUMN payment.payment.created_at IS '作成日時';
COMMENT ON COLUMN payment.payment.updated_at IS '更新日時';
