-- 送ったメール（docs/backend/modules/notification.md「1 通だけ送る」）
-- 注文は他モジュールの ID なので、外部キーは張らない（バックエンドのアーキテクチャ 第 3 節）
CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.sent_mail
(
    id        uuid         NOT NULL,
    order_id  uuid         NOT NULL,
    -- SHIPMENT（出荷通知メール）
    kind      varchar(20)  NOT NULL,
    recipient varchar(254) NOT NULL,
    sent_at   timestamptz  NOT NULL,
    CONSTRAINT sent_mail_pkey PRIMARY KEY (id),
    -- 同じ注文に同じ種類のメールは 1 通だけ
    CONSTRAINT sent_mail_order_id_kind_key UNIQUE (order_id, kind),
    CONSTRAINT sent_mail_kind_check CHECK (kind IN ('SHIPMENT'))
);

COMMENT ON TABLE notification.sent_mail IS '送ったメール';
COMMENT ON COLUMN notification.sent_mail.id IS '送ったメールの ID';
COMMENT ON COLUMN notification.sent_mail.order_id IS '注文 ID';
COMMENT ON COLUMN notification.sent_mail.kind IS 'メールの種類';
COMMENT ON COLUMN notification.sent_mail.recipient IS '宛先';
COMMENT ON COLUMN notification.sent_mail.sent_at IS '送った日時';
