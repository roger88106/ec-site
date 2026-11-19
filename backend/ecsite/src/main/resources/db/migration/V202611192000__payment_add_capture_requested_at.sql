-- 支払に、売上確定を求めた時刻を足す（spec 423）
-- 売上確定の応答が届かなかった支払を照合するため、売上確定を決済サービスに求めた時刻を覚える。照合で、決済サービスが売上を確定していなかったときに削除する。
-- 未払い・支払なしの行は、与信がないので値を持たない。
-- 部分索引は、照合の対象（与信済みで記録のある支払）を、時刻・支払 ID の順に件数の上限まで読むためのもの。
-- 列の追加と、今の行が満たす制約と索引の追加なので、前のリリースのアプリもそのまま動く。
ALTER TABLE payment.payment
    ADD COLUMN capture_requested_at timestamptz;

COMMENT ON COLUMN payment.payment.capture_requested_at IS '売上確定を求めた時刻（照合で、決済サービスが売上を確定していなかったときに削除する。与信がない状態では持たない）';

ALTER TABLE payment.payment ADD CONSTRAINT payment_capture_requested_at_check
    CHECK (capture_requested_at IS NULL OR status NOT IN ('UNPAID', 'NOT_PAID'));

CREATE INDEX payment_authorized_capture_requested_at_id_idx ON payment.payment (capture_requested_at, id)
    WHERE status = 'AUTHORIZED' AND capture_requested_at IS NOT NULL;
