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
