-- 注文のローカル用 seed
-- 会員 member@example.com（R__member_seed.sql）の注文。状態は、受付、キャンセルの申請中、申請の却下（受付のまま）、キャンセル済み（申請の承認）。
-- 金額は、税込の単価（税抜 × (100 + 率) / 100 の切り捨て）× 数量の合計に、送料（税込 1,000 円、標準税率）を足したもの。
-- 消費税は、率ごとの税込の合計（送料を含む）× 率 / (100 + 率) の切り捨て。在庫の出庫と戻しは R__inventory_seed.sql。
-- キャンセル済みの注文は、在庫に戻している（cancellation_restocked）。
-- 注文番号は注文日（東京）と 6 桁の番号。注文は画面や API で変えられるので、seed の再実行で上書きしない

INSERT INTO ordering.sales_order (id, order_number, checkout_id, ordered_at, status,
                                  items_total, shipping_fee, total_amount, email, recipient_name, postal_code, prefecture, address, phone,
                                  cancellation_requested_at, cancellation_request_reason, cancellation_request_result, cancellation_decided_at,
                                  cancelled_at, cancellation_kind, cancellation_reason,
                                  version, created_at, updated_at, member_id, cancellation_decided_by_id, cancellation_decided_by_name,
                                  cancelled_by_id, cancelled_by_name, cancellation_restocked)
VALUES ('00000000-0000-0000-0000-000000006101', to_char((now() - interval '6 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-107919', '00000000-0000-0000-0000-000000006201', now() - interval '6 days 0 hours', 'CANCELLED',
        2889, 1000, 3889, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '5 days 0 hours', '注文を取り消したい', 'APPROVED', now() - interval '5 days -2 hours', now() - interval '5 days -2 hours', 'REQUEST_APPROVED', NULL,
        0, now() - interval '6 days 0 hours', now() - interval '5 days -2 hours', '00000000-0000-0000-0000-000000006001', NULL, '管理 花子', NULL, '管理 花子', true),
       ('00000000-0000-0000-0000-000000006102', to_char((now() - interval '4 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-115838', '00000000-0000-0000-0000-000000006202', now() - interval '4 days 0 hours', 'ACCEPTED',
        6886, 1000, 7886, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '3 days 0 hours', '別の色にしたい', 'REJECTED', now() - interval '3 days -2 hours', NULL, NULL, NULL,
        0, now() - interval '4 days 0 hours', now() - interval '3 days -2 hours', '00000000-0000-0000-0000-000000006001', NULL, '管理 花子', NULL, NULL, NULL),
       ('00000000-0000-0000-0000-000000006103', to_char((now() - interval '2 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-123757', '00000000-0000-0000-0000-000000006203', now() - interval '2 days 0 hours', 'CANCELLATION_REQUESTED',
        8778, 1000, 9778, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        now() - interval '1 days 0 hours', 'サイズを間違えて注文した', 'PENDING', NULL, NULL, NULL, NULL,
        0, now() - interval '2 days 0 hours', now() - interval '1 days 0 hours', '00000000-0000-0000-0000-000000006001', NULL, NULL, NULL, NULL, NULL),
       ('00000000-0000-0000-0000-000000006104', to_char((now() - interval '1 days 0 hours') AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-131676', '00000000-0000-0000-0000-000000006204', now() - interval '1 days 0 hours', 'ACCEPTED',
        3602, 1000, 4602, 'member@example.com', '会員 太郎', '1500001', '東京都', '渋谷区神宮前 1-2-3', '0312345678',
        NULL, NULL, NULL, NULL, NULL, NULL, NULL,
        0, now() - interval '1 days 0 hours', now() - interval '1 days 0 hours', '00000000-0000-0000-0000-000000006001', NULL, NULL, NULL, NULL, NULL)
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

-- ============================== レポート用の注文 ==============================
-- 2024-10-01〜2026-09-30 の 20,000 件。README の「レポート用の注文」。
-- 乱数は、固定の文字列（番号と用途）の md5 から作る。何度作り直しても同じ注文になる。
-- ID は番号から作る：注文 00000000-0000-0000-0010-<番号 12 桁>、注文手続き 00000000-0000-0000-0011-<番号 12 桁>。
-- 会員は R__member_seed.sql のレポート用の会員（番号の小さい会員ほど多く注文する）。注文の時点の年齢と性別は、会員の番号から決める
-- （生年月日がない会員が 11 人に 1 人。性別は男性 42%、女性 42%、その他 4%、回答しない 6%、入れていない 6%）。
-- 日時の筋（T は注文日時。支払・出荷の seed と写しの seed は、この筋で注文の行から作る）：
--   支払待ち T → 受付 T + 3 分 → キャンセルの申請 T + 2 時間 → 承認・却下 T + 4 時間 → 運営者のキャンセル T + 6 時間
--   → 出荷 T + 1 日 → 返送による運営者のキャンセル T + 6 日。支払の期限切れのキャンセルは T + 35 分
-- 状態：期限切れ 3%、申請の承認 4%、申請の却下の後に出荷 2%、出荷の前の運営者のキャンセル 2%、出荷の後の返送でキャンセル 1.5%、
-- ほかは出荷済み。2026-09-29 以降の注文は出荷しておらず、受付（80%）かキャンセルの申請中。
-- 返送でのキャンセルは、理由を「受取拒否により返送されたため」にする（出荷の seed がこの理由で返送を作る）。
-- 在庫は、この注文の分を出庫していない（R__inventory_seed.sql の在庫は今の 4 件の注文の分だけ）。

CREATE FUNCTION pg_temp.seed_rnd(key text) RETURNS double precision
    LANGUAGE sql IMMUTABLE
AS $$ SELECT ('x' || substr(md5(key), 1, 8))::bit(32)::bigint / 4294967296.0 $$;

-- お届け先：実在する郵便番号・都道府県・市区町村（README の「お届け先」）と、重み
CREATE TEMP TABLE seed_address ON COMMIT DROP AS
SELECT ord, postal_code, prefecture, city, town, weight,
       sum(weight) OVER (ORDER BY ord) - weight AS cum_from, sum(weight) OVER (ORDER BY ord) AS cum_to
FROM (VALUES (1, '1000001', '東京都', '千代田区', '千代田', 14), (2, '1500001', '東京都', '渋谷区', '神宮前', 14),
             (3, '1600022', '東京都', '新宿区', '新宿', 14), (4, '1700013', '東京都', '豊島区', '東池袋', 14),
             (5, '1350091', '東京都', '港区', '台場', 14), (6, '1080075', '東京都', '港区', '港南', 14),
             (7, '1300015', '東京都', '墨田区', '横網', 14), (8, '1580094', '東京都', '世田谷区', '玉川', 14),
             (9, '1920083', '東京都', '八王子市', '旭町', 14), (10, '1800004', '東京都', '武蔵野市', '吉祥寺本町', 14),
             (11, '2200012', '神奈川県', '横浜市西区', 'みなとみらい', 18), (12, '2310023', '神奈川県', '横浜市中区', '山下町', 18),
             (13, '2100007', '神奈川県', '川崎市川崎区', '駅前本町', 18), (14, '2510025', '神奈川県', '藤沢市', '鵠沼石上', 18),
             (15, '2520303', '神奈川県', '相模原市南区', '相模大野', 18),
             (16, '5300001', '大阪府', '大阪市北区', '梅田', 18), (17, '5420076', '大阪府', '大阪市中央区', '難波', 18),
             (18, '5900028', '大阪府', '堺市堺区', '三国ヶ丘御幸通', 18), (19, '5600021', '大阪府', '豊中市', '本町', 18),
             (20, '5730032', '大阪府', '枚方市', '岡東町', 18),
             (21, '4500002', '愛知県', '名古屋市中村区', '名駅', 19), (22, '4600008', '愛知県', '名古屋市中区', '栄', 19),
             (23, '4710025', '愛知県', '豊田市', '西町', 19), (24, '4400888', '愛知県', '豊橋市', '駅前大通', 19),
             (25, '3300802', '埼玉県', 'さいたま市大宮区', '宮町', 24), (26, '3500043', '埼玉県', '川越市', '新富町', 24),
             (27, '3320017', '埼玉県', '川口市', '栄町', 24),
             (28, '2600013', '千葉県', '千葉市中央区', '中央', 16), (29, '2790031', '千葉県', '浦安市', '舞浜', 16),
             (30, '2730005', '千葉県', '船橋市', '本町', 16), (31, '2770005', '千葉県', '柏市', '柏', 16),
             (32, '0600001', '北海道', '札幌市中央区', '北一条西', 26), (33, '0700031', '北海道', '旭川市', '１条通', 26),
             (34, '8100001', '福岡県', '福岡市中央区', '天神', 17), (35, '8120012', '福岡県', '福岡市博多区', '博多駅中央街', 17),
             (36, '8020001', '福岡県', '北九州市小倉北区', '浅野', 17),
             (37, '6008216', '京都府', '京都市下京区', '東塩小路町', 25),
             (38, '6500044', '兵庫県', '神戸市中央区', '東川崎町', 27), (39, '6620911', '兵庫県', '西宮市', '池田町', 27),
             (40, '9800021', '宮城県', '仙台市青葉区', '中央', 23), (41, '7300011', '広島県', '広島市中区', '基町', 27),
             (42, '4200858', '静岡県', '静岡市葵区', '伝馬町', 36), (43, '3100015', '茨城県', '水戸市', '宮町', 28),
             (44, '9500087', '新潟県', '新潟市中央区', '東大通', 21), (45, '3200802', '栃木県', '宇都宮市', '江野町', 19),
             (46, '3700849', '群馬県', '高崎市', '八島町', 19), (47, '7000024', '岡山県', '岡山市北区', '駅元町', 19),
             (48, '8600047', '熊本県', '熊本市西区', '春日', 17), (49, '9000006', '沖縄県', '那覇市', 'おもろまち', 15),
             (50, '3800824', '長野県', '長野市', '南石堂町', 20), (51, '5100075', '三重県', '四日市市', '安島', 17),
             (52, '8900053', '鹿児島県', '鹿児島市', '中央町', 15), (53, '9200858', '石川県', '金沢市', '木ノ新保町', 11),
             (54, '7900001', '愛媛県', '松山市', '一番町', 13), (55, '5200044', '滋賀県', '大津市', '京町', 14))
         AS a (ord, postal_code, prefecture, city, town, weight);

-- 売る商品：公開中の商品のバリアント。商品ごとに売れやすさの重み（1〜5）を付ける
-- 税率は、税区分の固定の ID（db/seed/base の標準 10%、軽減 8%）から決める。税率の seed（R__tax_seed.sql）は名前の順でこの後に流れるため
CREATE TEMP TABLE seed_variant ON COMMIT DROP AS
WITH variant AS (SELECT v.id AS variant_id, p.product_code, v.variant_code, p.name AS product_name, p.price_excl_tax,
                        CASE p.tax_category_id WHEN '00000000-0000-0000-0000-000000000002' THEN 8 ELSE 10 END::numeric(4, 2) AS rate_percent,
                        coalesce((SELECT jsonb_agg(jsonb_build_object('name', axis.name, 'value', axis_value.value)
                                                   ORDER BY axis_value.position)
                                  FROM catalog.variant_axis_value axis_value
                                           JOIN catalog.product_axis axis
                                                ON axis.product_id = p.id AND axis.position = axis_value.position
                                  WHERE axis_value.variant_id = v.id), '[]'::jsonb) AS axis_values,
                        1 + floor(pg_temp.seed_rnd('product:' || p.product_code) * 5) AS weight
                 FROM catalog.variant v
                          JOIN catalog.product p ON p.id = v.product_id
                 WHERE p.status = 'PUBLISHED' AND p.publish_start IS NULL)
SELECT row_number() OVER (ORDER BY variant_code) - 1 AS idx, variant.*,
       sum(weight) OVER (ORDER BY variant_code) - weight AS cum_from, sum(weight) OVER (ORDER BY variant_code) AS cum_to
FROM variant;

-- 注文の骨組み：日時（後ほど多い）、会員、状態の筋（plan）、注文の時点の年齢と性別、お届け先
CREATE TEMP TABLE seed_order ON COMMIT DROP AS
WITH base AS (SELECT n,
                     timestamptz '2024-10-01 00:00:00+09'
                         + (sqrt(1 + 3 * ((n - 1 + pg_temp.seed_rnd('t:' || n)) / 20000.0)) - 1) * 730 * interval '1 day' AS t,
                     1 + floor(300 * pg_temp.seed_rnd('member:' || n) ^ 2)::int AS m,
                     pg_temp.seed_rnd('status:' || n) AS u
              FROM generate_series(1, 20000) AS n),
     member AS (SELECT m,
                       CASE WHEN m % 11 = 0 THEN NULL
                            ELSE date '1948-01-01' + floor(pg_temp.seed_rnd('birth:' || m) * 22000)::int END AS birthdate,
                       pg_temp.seed_rnd('gender:' || m) AS g,
                       pg_temp.seed_rnd('address:' || m) * (SELECT max(cum_to) FROM seed_address) AS a
                FROM generate_series(1, 300) AS m)
SELECT base.n, base.t, base.m,
       CASE WHEN base.t >= timestamptz '2026-09-29 00:00:00+09' THEN CASE WHEN base.u < 0.8 THEN 'ACCEPTED' ELSE 'REQUESTED' END
            WHEN base.u < 0.03 THEN 'EXPIRED'
            WHEN base.u < 0.07 THEN 'APPROVED'
            WHEN base.u < 0.09 THEN 'REJECTED'
            WHEN base.u < 0.11 THEN 'OPERATOR'
            WHEN base.u < 0.125 THEN 'RETURNED'
            ELSE 'SHIPPED' END AS plan,
       ('00000000-0000-0000-0010-' || lpad(base.n::text, 12, '0'))::uuid AS id,
       ('00000000-0000-0000-0001-' || lpad(base.m::text, 12, '0'))::uuid AS member_id,
       CASE WHEN member.birthdate IS NOT NULL
                THEN extract(YEAR FROM age((base.t AT TIME ZONE 'Asia/Tokyo')::date, member.birthdate))::smallint END AS buyer_age,
       CASE WHEN member.g < 0.42 THEN 'MALE' WHEN member.g < 0.84 THEN 'FEMALE' WHEN member.g < 0.88 THEN 'OTHER'
            WHEN member.g < 0.94 THEN 'UNDISCLOSED' END AS buyer_gender,
       address.postal_code, address.prefecture,
       address.city || address.town || ' ' || (base.m % 9 + 1) || '-' || (base.m % 17 + 1) || '-' || (base.m % 23 + 1) AS address
FROM base
         JOIN member ON member.m = base.m
         JOIN seed_address address ON member.a >= address.cum_from AND member.a < address.cum_to;

-- 明細：1〜3 行。1 行目は重みで選び、2 行目からは並びで 7 つずつずらした別のバリアント。数量は 1 が多い
CREATE TEMP TABLE seed_line ON COMMIT DROP AS
WITH line_count AS (SELECT n,
                           CASE WHEN pg_temp.seed_rnd('lines:' || n) < 0.55 THEN 1
                                WHEN pg_temp.seed_rnd('lines:' || n) < 0.85 THEN 2 ELSE 3 END AS lines,
                           pg_temp.seed_rnd('variant:' || n) * (SELECT max(cum_to) FROM seed_variant) AS v
                    FROM seed_order),
     line AS (SELECT line_count.n, line_no,
                     (first.idx + (line_no - 1) * 7) % (SELECT count(*) FROM seed_variant) AS idx,
                     1 + floor(pg_temp.seed_rnd('quantity:' || line_count.n || ':' || line_no) ^ 3 * 4)::int AS quantity
              FROM line_count
                       JOIN seed_variant first ON line_count.v >= first.cum_from AND line_count.v < first.cum_to
                       CROSS JOIN generate_series(1, 3) AS line_no
              WHERE line_no <= line_count.lines)
SELECT line.n, line.line_no::smallint AS line_no, variant.variant_id, variant.product_code, variant.variant_code,
       variant.product_name, variant.axis_values, variant.price_excl_tax AS unit_price_excl_tax, variant.rate_percent,
       floor(variant.price_excl_tax * (100 + variant.rate_percent) / 100)::bigint AS unit_price_incl_tax, line.quantity,
       floor(variant.price_excl_tax * (100 + variant.rate_percent) / 100)::bigint * line.quantity AS subtotal
FROM line
         JOIN seed_variant variant ON variant.idx = line.idx;

-- 消費税：率ごとの税込の合計（送料は標準税率）× 率 / (100 + 率) の切り捨て
CREATE TEMP TABLE seed_tax ON COMMIT DROP AS
SELECT n, rate_percent, taxable_amount, floor(taxable_amount * rate_percent / (100 + rate_percent))::bigint AS tax_amount
FROM (SELECT n, rate_percent, sum(subtotal) + CASE WHEN rate_percent = 10 THEN 1000 ELSE 0 END AS taxable_amount
      FROM (SELECT n, rate_percent, subtotal FROM seed_line
            UNION ALL
            SELECT n, 10, 0 FROM seed_order) AS amount
      GROUP BY n, rate_percent) AS taxable;

INSERT INTO ordering.sales_order (id, order_number, checkout_id, ordered_at, status,
                                  items_total, shipping_fee, total_amount, email, recipient_name, postal_code, prefecture, address, phone,
                                  cancellation_requested_at, cancellation_request_reason, cancellation_request_result, cancellation_decided_at,
                                  cancelled_at, cancellation_kind, cancellation_reason,
                                  version, created_at, updated_at, member_id, cancellation_decided_by_id, cancellation_decided_by_name,
                                  cancelled_by_id, cancelled_by_name, cancellation_restocked, buyer_age, buyer_gender)
SELECT o.id,
       to_char(o.t AT TIME ZONE 'Asia/Tokyo', 'YYYYMMDD') || '-' || lpad(((o.n * 7919) % 1000000)::text, 6, '0'),
       ('00000000-0000-0000-0011-' || lpad(o.n::text, 12, '0'))::uuid,
       o.t,
       CASE o.plan WHEN 'ACCEPTED' THEN 'ACCEPTED' WHEN 'REQUESTED' THEN 'CANCELLATION_REQUESTED'
                   WHEN 'REJECTED' THEN 'SHIPPED' WHEN 'SHIPPED' THEN 'SHIPPED' ELSE 'CANCELLED' END,
       items.total, 1000, items.total + 1000,
       'report-member-' || o.m || '@example.com', 'レポート 会員' || o.m, o.postal_code, o.prefecture, o.address,
       '090' || lpad(o.m::text, 8, '0'),
       CASE WHEN o.plan IN ('REQUESTED', 'APPROVED', 'REJECTED') THEN o.t + interval '2 hours' END,
       CASE WHEN o.plan IN ('REQUESTED', 'APPROVED', 'REJECTED') THEN 'サイズを間違えて注文した' END,
       CASE o.plan WHEN 'REQUESTED' THEN 'PENDING' WHEN 'APPROVED' THEN 'APPROVED' WHEN 'REJECTED' THEN 'REJECTED' END,
       CASE WHEN o.plan IN ('APPROVED', 'REJECTED') THEN o.t + interval '4 hours' END,
       CASE o.plan WHEN 'EXPIRED' THEN o.t + interval '35 minutes' WHEN 'APPROVED' THEN o.t + interval '4 hours'
                   WHEN 'OPERATOR' THEN o.t + interval '6 hours' WHEN 'RETURNED' THEN o.t + interval '6 days' END,
       CASE o.plan WHEN 'EXPIRED' THEN 'PAYMENT_EXPIRED' WHEN 'APPROVED' THEN 'REQUEST_APPROVED'
                   WHEN 'OPERATOR' THEN 'OPERATOR' WHEN 'RETURNED' THEN 'OPERATOR' END,
       CASE o.plan WHEN 'OPERATOR' THEN '商品が破損していたため' WHEN 'RETURNED' THEN '受取拒否により返送されたため' END,
       0, o.t,
       CASE o.plan WHEN 'ACCEPTED' THEN o.t + interval '3 minutes' WHEN 'REQUESTED' THEN o.t + interval '2 hours'
                   WHEN 'EXPIRED' THEN o.t + interval '35 minutes' WHEN 'APPROVED' THEN o.t + interval '4 hours'
                   WHEN 'OPERATOR' THEN o.t + interval '6 hours' WHEN 'RETURNED' THEN o.t + interval '6 days'
                   ELSE o.t + interval '1 day' END,
       o.member_id,
       NULL, CASE WHEN o.plan IN ('APPROVED', 'REJECTED') THEN '管理 花子' END,
       NULL, CASE WHEN o.plan IN ('OPERATOR', 'RETURNED') THEN '管理 花子' END,
       CASE o.plan WHEN 'EXPIRED' THEN true WHEN 'APPROVED' THEN true WHEN 'OPERATOR' THEN true
                   WHEN 'RETURNED' THEN o.n % 2 = 0 END,
       o.buyer_age, o.buyer_gender
FROM seed_order o
         JOIN (SELECT n, sum(subtotal) AS total FROM seed_line GROUP BY n) AS items ON items.n = o.n
ON CONFLICT (id) DO NOTHING;

INSERT INTO ordering.sales_order_line (order_id, line_no, variant_id, product_code, variant_code, product_name, axis_values,
                                       unit_price_excl_tax, rate_percent, unit_price_incl_tax, quantity, subtotal)
SELECT ('00000000-0000-0000-0010-' || lpad(n::text, 12, '0'))::uuid, line_no, variant_id, product_code, variant_code,
       product_name, axis_values, unit_price_excl_tax, rate_percent, unit_price_incl_tax, quantity, subtotal
FROM seed_line
ON CONFLICT (order_id, line_no) DO NOTHING;

INSERT INTO ordering.sales_order_tax (order_id, rate_percent, taxable_amount, tax_amount)
SELECT ('00000000-0000-0000-0010-' || lpad(n::text, 12, '0'))::uuid, rate_percent, taxable_amount, tax_amount
FROM seed_tax
ON CONFLICT (order_id, rate_percent) DO NOTHING;
