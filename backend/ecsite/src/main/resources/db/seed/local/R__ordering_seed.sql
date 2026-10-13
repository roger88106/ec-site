-- 注文のローカル用 seed
-- 会員 member@example.com（R__member_seed.sql）の注文。状態は、受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み（申請の承認）。
-- 金額は、税込の単価（税抜 × (100 + 率) / 100 の切り捨て）× 数量の合計に、送料（税込 1,000 円、標準税率）を足したもの。
-- 消費税は、率ごとの税込の合計（送料を含む）× 率 / (100 + 率) の切り捨て。在庫の出庫と戻しは R__inventory_seed.sql。
-- 注文番号は注文日（東京）と 6 桁の番号。注文は画面や API で変えられるので、seed の再実行で上書きしない

INSERT INTO ordering.sales_order (id, order_number, checkout_id, ordered_at, status,
                                  items_total, shipping_fee, total_amount, email, recipient_name, postal_code, prefecture, address, phone,
                                  cancellation_requested_at, cancellation_request_reason, cancellation_request_result, cancellation_decided_at,
                                  cancelled_at, cancellation_kind, cancellation_reason,
                                  version, created_at, updated_at, member_id, cancellation_decided_by_id, cancellation_decided_by_name,
                                  cancelled_by_id, cancelled_by_name)
VALUES ('00000000-0000-0000-0000-000000006101', to_char((now() - interval '6 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-107919', '00000000-0000-0000-0000-000000006201', now() - interval '6 days 0 hours', 'CANCELLED',
        2889, 1000, 3889, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '5 days 0 hours', '注文を取り消したい', 'APPROVED', now() - interval '5 days -2 hours', now() - interval '5 days -2 hours', 'REQUEST_APPROVED', NULL,
        0, now() - interval '6 days 0 hours', now() - interval '5 days -2 hours', '00000000-0000-0000-0000-000000006001', NULL, '管理 花子', NULL, '管理 花子'),
       ('00000000-0000-0000-0000-000000006102', to_char((now() - interval '4 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-115838', '00000000-0000-0000-0000-000000006202', now() - interval '4 days 0 hours', 'ACCEPTED',
        6886, 1000, 7886, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '3 days 0 hours', '別の色にしたい', 'REJECTED', now() - interval '3 days -2 hours', NULL, NULL, NULL,
        0, now() - interval '4 days 0 hours', now() - interval '3 days -2 hours', '00000000-0000-0000-0000-000000006001', NULL, '管理 花子', NULL, NULL),
       ('00000000-0000-0000-0000-000000006103', to_char((now() - interval '2 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-123757', '00000000-0000-0000-0000-000000006203', now() - interval '2 days 0 hours', 'CANCELLATION_REQUESTED',
        8778, 1000, 9778, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '1 days 0 hours', 'サイズを間違えて注文した', 'PENDING', NULL, NULL, NULL, NULL,
        0, now() - interval '2 days 0 hours', now() - interval '1 days 0 hours', '00000000-0000-0000-0000-000000006001', NULL, NULL, NULL, NULL),
       ('00000000-0000-0000-0000-000000006104', to_char((now() - interval '1 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-131676', '00000000-0000-0000-0000-000000006204', now() - interval '1 days 0 hours', 'ACCEPTED',
        3602, 1000, 4602, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        NULL, NULL, NULL, NULL, NULL, NULL, NULL,
        0, now() - interval '1 days 0 hours', now() - interval '1 days 0 hours', '00000000-0000-0000-0000-000000006001', NULL, NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ordering.sales_order_line (order_id, line_no, variant_id, product_code, variant_code, product_name, axis_values,
                                       unit_price_excl_tax, rate_percent, unit_price_incl_tax, quantity, subtotal)
VALUES ('00000000-0000-0000-0000-000000006101', 1, '00000000-0000-0000-0000-000000003111', 'CHOCO-001', 'CHOCO-001', '板チョコレート ミルク',
        '[]', 220, 8, 237, 3, 711),
       ('00000000-0000-0000-0000-000000006101', 2, '00000000-0000-0000-0000-000000003141', 'CANDLE-001', 'CANDLE-001', 'アロマキャンドル',
        '[]', 1980, 10, 2178, 1, 2178),
       ('00000000-0000-0000-0000-000000006102', 1, '00000000-0000-0000-0000-000000003081', 'SKIRT-001', 'SKIRT-001-NV', 'プリーツスカート',
        '[{"name":"色","value":"ネイビー"}]', 5480, 10, 6028, 1, 6028),
       ('00000000-0000-0000-0000-000000006102', 2, '00000000-0000-0000-0000-000000003101', 'JUICE-001', 'JUICE-001', 'りんごジュース 1L',
        '[]', 398, 8, 429, 2, 858),
       ('00000000-0000-0000-0000-000000006103', 1, '00000000-0000-0000-0000-000000003072', 'PANTS-002', 'PANTS-002-30', 'デニムパンツ',
        '[{"name":"サイズ","value":"30"}]', 7980, 10, 8778, 1, 8778),
       ('00000000-0000-0000-0000-000000006104', 1, '00000000-0000-0000-0000-000000003001', 'TEE-001', 'TEE-001-WH-M', 'ベーシック T シャツ',
        '[{"name":"色","value":"白"},{"name":"サイズ","value":"M"}]', 2980, 10, 3278, 1, 3278),
       ('00000000-0000-0000-0000-000000006104', 2, '00000000-0000-0000-0000-000000003021', 'TEA-001', 'TEA-001', '緑茶 500ml',
        '[]', 150, 8, 162, 2, 324)
ON CONFLICT (order_id, line_no) DO NOTHING;

INSERT INTO ordering.sales_order_tax (order_id, rate_percent, taxable_amount, tax_amount)
VALUES ('00000000-0000-0000-0000-000000006101', 10, 3178, 288),
       ('00000000-0000-0000-0000-000000006101', 8, 711, 52),
       ('00000000-0000-0000-0000-000000006102', 10, 7028, 638),
       ('00000000-0000-0000-0000-000000006102', 8, 858, 63),
       ('00000000-0000-0000-0000-000000006103', 10, 9778, 888),
       ('00000000-0000-0000-0000-000000006104', 10, 4278, 388),
       ('00000000-0000-0000-0000-000000006104', 8, 324, 24)
ON CONFLICT (order_id, rate_percent) DO NOTHING;
