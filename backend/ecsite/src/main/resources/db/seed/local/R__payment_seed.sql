-- 支払のローカル用 seed
-- R__ordering_seed.sql の注文の支払。金額は注文の総額、支払の期限は注文日時の 30 分後（既定）、与信の時刻は注文日時。
-- 状態は、キャンセル済みの注文が与信の取消済み、受付とキャンセルの申請中の注文が与信済み。
-- 支払の画面 ID と与信の参照は mock の形。売上確定（出荷）は mock でだけ通り、Stripe では与信がないので失敗する。
-- 与信の時刻は seed を流した時刻から数えるので、DB を数日使い続けると与信期限が近い印が出る（DB を作り直せば戻る）。
-- 支払は出荷やキャンセルで変わるので、seed の再実行で上書きしない

INSERT INTO payment.payment (id, order_id, amount, deadline, status, page_id, page_url, authorization_ref, authorized_at,
                             version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000006401', '00000000-0000-0000-0000-000000006101', 3889, now() - interval '6 days 0 hours' + interval '30 minutes', 'VOIDED',
        'mock_page_00000000-0000-0000-0000-000000006401', 'http://localhost:8080/mock-payment/mock_page_00000000-0000-0000-0000-000000006401',
        'mock_auth_00000000-0000-0000-0000-000000006401', now() - interval '6 days 0 hours',
        0, now() - interval '6 days 0 hours', now() - interval '5 days -2 hours'),
       ('00000000-0000-0000-0000-000000006402', '00000000-0000-0000-0000-000000006102', 7886, now() - interval '4 days 0 hours' + interval '30 minutes', 'AUTHORIZED',
        'mock_page_00000000-0000-0000-0000-000000006402', 'http://localhost:8080/mock-payment/mock_page_00000000-0000-0000-0000-000000006402',
        'mock_auth_00000000-0000-0000-0000-000000006402', now() - interval '4 days 0 hours',
        0, now() - interval '4 days 0 hours', now() - interval '4 days 0 hours'),
       ('00000000-0000-0000-0000-000000006403', '00000000-0000-0000-0000-000000006103', 9778, now() - interval '2 days 0 hours' + interval '30 minutes', 'AUTHORIZED',
        'mock_page_00000000-0000-0000-0000-000000006403', 'http://localhost:8080/mock-payment/mock_page_00000000-0000-0000-0000-000000006403',
        'mock_auth_00000000-0000-0000-0000-000000006403', now() - interval '2 days 0 hours',
        0, now() - interval '2 days 0 hours', now() - interval '2 days 0 hours'),
       ('00000000-0000-0000-0000-000000006404', '00000000-0000-0000-0000-000000006104', 4602, now() - interval '1 days 0 hours' + interval '30 minutes', 'AUTHORIZED',
        'mock_page_00000000-0000-0000-0000-000000006404', 'http://localhost:8080/mock-payment/mock_page_00000000-0000-0000-0000-000000006404',
        'mock_auth_00000000-0000-0000-0000-000000006404', now() - interval '1 days 0 hours',
        0, now() - interval '1 days 0 hours', now() - interval '1 days 0 hours')
ON CONFLICT (order_id) DO NOTHING;

-- レポート用の注文（R__ordering_seed.sql の spec 010 の注文）の支払。注文の状態とキャンセルから作る。
-- ID は 00000000-0000-0000-0012-<注文の番号 12 桁>。期限は注文日時の 30 分後、与信は注文日時の 3 分後（受付と同じ時刻）。
-- 受付・キャンセルの申請中は与信済み、出荷済みは売上確定済み（出荷の時刻）、期限切れのキャンセルは支払なし、
-- 出荷の前のキャンセルは与信の取消済み、返送でのキャンセル（キャンセルの理由で見分ける）は返金済み。
INSERT INTO payment.payment (id, order_id, amount, deadline, status, page_id, page_url, authorization_ref, authorized_at,
                             version, created_at, updated_at)
SELECT ('00000000-0000-0000-0012-' || right(o.id::text, 12))::uuid,
       o.id,
       o.total_amount,
       o.ordered_at + interval '30 minutes',
       payment.status,
       'mock_page_00000000-0000-0000-0012-' || right(o.id::text, 12),
       'http://localhost:8080/mock-payment/mock_page_00000000-0000-0000-0012-' || right(o.id::text, 12),
       CASE WHEN payment.status <> 'NOT_PAID' THEN 'mock_auth_00000000-0000-0000-0012-' || right(o.id::text, 12) END,
       CASE WHEN payment.status <> 'NOT_PAID' THEN o.ordered_at + interval '3 minutes' END,
       0,
       o.ordered_at,
       CASE payment.status WHEN 'AUTHORIZED' THEN o.ordered_at + interval '3 minutes'
                           WHEN 'CAPTURED' THEN o.ordered_at + interval '1 day'
                           ELSE o.cancelled_at END
FROM ordering.sales_order o
         CROSS JOIN LATERAL (SELECT CASE WHEN o.status IN ('ACCEPTED', 'CANCELLATION_REQUESTED') THEN 'AUTHORIZED'
                                         WHEN o.status = 'SHIPPED' THEN 'CAPTURED'
                                         WHEN o.cancellation_kind = 'PAYMENT_EXPIRED' THEN 'NOT_PAID'
                                         WHEN o.cancellation_reason = '受取拒否により返送されたため' THEN 'REFUNDED'
                                         ELSE 'VOIDED' END AS status) AS payment
WHERE o.id::text LIKE '00000000-0000-0000-0010-%'
ON CONFLICT (order_id) DO NOTHING;
