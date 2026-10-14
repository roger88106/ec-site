-- 支払に与信の時刻を足す。与信期限（与信の時刻 ＋ 有効期間）の起点
ALTER TABLE payment.payment
    ADD COLUMN authorized_at timestamptz;

-- 売上確定済み・与信期限切れ・返金待ち・返金済みを足すと、状態の名前が 20 文字を超える（AUTHORIZATION_EXPIRED）
ALTER TABLE payment.payment
    ALTER COLUMN status TYPE varchar(30);

-- すでに与信済みの支払は、与信の時刻を覚えていないので、最後に更新した時刻で近似する
UPDATE payment.payment
SET authorized_at = updated_at
WHERE status = 'AUTHORIZED';

-- 与信期限が近い支払を探すため
CREATE INDEX payment_authorized_at_idx ON payment.payment (authorized_at)
    WHERE status IN ('AUTHORIZED', 'AUTHORIZATION_EXPIRED');

COMMENT ON COLUMN payment.payment.status IS '支払の状態（UNPAID / AUTHORIZED / VOID_PENDING / VOIDED / NOT_PAID / CAPTURED / AUTHORIZATION_EXPIRED / REFUND_PENDING / REFUNDED）';
COMMENT ON COLUMN payment.payment.authorized_at IS '与信の時刻';
