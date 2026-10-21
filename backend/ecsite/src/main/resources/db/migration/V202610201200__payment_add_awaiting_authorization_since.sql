-- 支払に、与信の通知を待ち始めた時刻を足す（spec 017 AC-311、docs/design/modules/payment.md「支払の期限」）
-- 期限切れのジョブが、決済サービスで支払済みと知った時刻。この時刻のある支払は、期限切れの支払の検索から除く。
-- 未払いの支払は少ないので、索引は足さない（未払いの期限の索引 payment_unpaid_deadline_idx で絞れる）
ALTER TABLE payment.payment
    ADD COLUMN awaiting_authorization_since timestamptz;

COMMENT ON COLUMN payment.payment.awaiting_authorization_since IS '与信の通知を待ち始めた時刻（決済サービスで支払済みのため、期限切れにできなかった時刻）';
