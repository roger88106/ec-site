-- レポートの写しと状態の履歴のローカル用 seed（spec 010）
-- seed で入れた注文には「注文の状態の変化」「配送状態の変化」のイベントがないので、注文・支払・出荷の seed から、
-- イベントを受けたときと同じ写しと履歴を作る。seed はモジュールの外のデータなので、他のモジュールの表を読んでよい。
-- 出荷の seed（R__shipping_seed.sql）より後に流すため、名前を zz で始める（Flyway は繰り返し実行のマイグレーションを名前の順に流す）。
-- 状態の履歴の日時：
--   支払待ち 注文日時 → 受付 与信の時刻 → キャンセルの申請 申請の日時 → 却下で受付 判断の日時 → 出荷済み 出荷の日時
--   → キャンセル キャンセルの日時（直前の状態は、期限切れなら支払待ち、申請の承認ならキャンセルの申請、出荷があれば出荷済み、ほかは受付）
--   配送状態：出荷済み 出荷の日時 → 配送中 出荷の 6 時間後 → 配達完了・返送 最後の通知の日時（R__shipping_seed.sql の筋）
-- イベント ID は、注文 ID と履歴の種類の md5 から作る。写しはイベントで作り直されるので、seed の再実行で上書きしない

CREATE TEMP TABLE seed_history ON COMMIT DROP AS
WITH source AS (SELECT o.*, p.authorized_at, s.shipped_at, s.status AS delivery_status, s.last_notified_at
                FROM ordering.sales_order o
                         LEFT JOIN payment.payment p ON p.order_id = o.id
                         LEFT JOIN shipping.shipment s ON s.order_id = o.id),
     history AS (SELECT id, 'placed' AS step, 'ORDER_STATUS' AS kind, NULL AS from_status, 'PENDING_PAYMENT' AS to_status,
                        ordered_at AS occurred_at, NULL AS cancellation_kind, NULL::boolean AS restocked
                 FROM source
                 UNION ALL
                 SELECT id, 'accepted', 'ORDER_STATUS', 'PENDING_PAYMENT', 'ACCEPTED', authorized_at, NULL, NULL
                 FROM source
                 WHERE authorized_at IS NOT NULL
                 UNION ALL
                 SELECT id, 'requested', 'ORDER_STATUS', 'ACCEPTED', 'CANCELLATION_REQUESTED', cancellation_requested_at, NULL, NULL
                 FROM source
                 WHERE cancellation_requested_at IS NOT NULL
                 UNION ALL
                 SELECT id, 'rejected', 'ORDER_STATUS', 'CANCELLATION_REQUESTED', 'ACCEPTED', cancellation_decided_at, NULL, NULL
                 FROM source
                 WHERE cancellation_request_result = 'REJECTED'
                 UNION ALL
                 SELECT id, 'shipped', 'ORDER_STATUS', 'ACCEPTED', 'SHIPPED', shipped_at, NULL, NULL
                 FROM source
                 WHERE shipped_at IS NOT NULL
                 UNION ALL
                 SELECT id, 'cancelled', 'ORDER_STATUS',
                        CASE WHEN cancellation_kind = 'PAYMENT_EXPIRED' THEN 'PENDING_PAYMENT'
                             WHEN cancellation_kind = 'REQUEST_APPROVED' THEN 'CANCELLATION_REQUESTED'
                             WHEN shipped_at IS NOT NULL THEN 'SHIPPED'
                             ELSE 'ACCEPTED' END,
                        'CANCELLED', cancelled_at, cancellation_kind, cancellation_restocked
                 FROM source
                 WHERE cancelled_at IS NOT NULL
                 UNION ALL
                 SELECT id, 'delivery-shipped', 'DELIVERY_STATUS', NULL, 'SHIPPED', shipped_at, NULL, NULL
                 FROM source
                 WHERE shipped_at IS NOT NULL
                 UNION ALL
                 SELECT id, 'delivery-in-transit', 'DELIVERY_STATUS', 'SHIPPED', 'IN_TRANSIT', shipped_at + interval '6 hours', NULL, NULL
                 FROM source
                 WHERE delivery_status IN ('IN_TRANSIT', 'DELIVERED', 'RETURNED')
                 UNION ALL
                 SELECT id, 'delivery-' || lower(delivery_status), 'DELIVERY_STATUS', 'IN_TRANSIT', delivery_status, last_notified_at,
                        NULL, NULL
                 FROM source
                 WHERE delivery_status IN ('DELIVERED', 'RETURNED'))
SELECT md5(id || ':' || step)::uuid AS event_id, id AS order_id, kind, from_status, to_status, occurred_at, cancellation_kind,
       restocked
FROM history;

-- 都道府県の名前から全国地方公共団体コード（reporting の PrefectureCode と同じ）
CREATE TEMP TABLE seed_prefecture ON COMMIT DROP AS
SELECT name, lpad(code::text, 2, '0') AS code
FROM unnest(ARRAY ['北海道', '青森県', '岩手県', '宮城県', '秋田県', '山形県', '福島県', '茨城県', '栃木県', '群馬県',
                   '埼玉県', '千葉県', '東京都', '神奈川県', '新潟県', '富山県', '石川県', '福井県', '山梨県', '長野県',
                   '岐阜県', '静岡県', '愛知県', '三重県', '滋賀県', '京都府', '大阪府', '兵庫県', '奈良県', '和歌山県',
                   '鳥取県', '島根県', '岡山県', '広島県', '山口県', '徳島県', '香川県', '愛媛県', '高知県', '福岡県',
                   '佐賀県', '長崎県', '熊本県', '大分県', '宮崎県', '鹿児島県', '沖縄県']) WITH ORDINALITY AS p (name, code);

INSERT INTO reporting.status_history (event_id, order_id, kind, from_status, to_status, occurred_at, cancellation_kind, restocked,
                                      recorded_at)
SELECT event_id, order_id, kind, from_status, to_status, occurred_at, cancellation_kind, restocked, occurred_at
FROM seed_history
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO reporting.sales_order (order_id, order_number, member_id, ordered_at, ordered_on, status, shipped_at, shipped_on,
                                   delivery_status, cancelled_at, cancellation_kind, restocked, postal_code, prefecture_code,
                                   buyer_age, buyer_gender, total_amount, shipping_fee, tax_total, shipping_tax, refreshed_at)
SELECT o.id, o.order_number, o.member_id, o.ordered_at, (o.ordered_at AT TIME ZONE 'Asia/Tokyo')::date, o.status,
       s.shipped_at, (s.shipped_at AT TIME ZONE 'Asia/Tokyo')::date, s.status,
       o.cancelled_at, o.cancellation_kind, o.cancellation_restocked, o.postal_code, prefecture.code,
       o.buyer_age, o.buyer_gender, o.total_amount, o.shipping_fee,
       tax.tax_total,
       -- 送料の消費税：送料 × 標準税率 / (100 + 標準税率) の切り捨て
       floor(o.shipping_fee * 10 / 110.0)::bigint,
       history.refreshed_at
FROM ordering.sales_order o
         JOIN (SELECT order_id, sum(tax_amount) AS tax_total FROM ordering.sales_order_tax GROUP BY order_id) AS tax
              ON tax.order_id = o.id
         JOIN (SELECT order_id, max(occurred_at) AS refreshed_at FROM seed_history GROUP BY order_id) AS history
              ON history.order_id = o.id
         LEFT JOIN shipping.shipment s ON s.order_id = o.id
         LEFT JOIN seed_prefecture prefecture ON prefecture.name = o.prefecture
ON CONFLICT (order_id) DO NOTHING;

INSERT INTO reporting.sales_order_line (order_id, line_no, product_id, variant_id, product_code, variant_code, product_name,
                                        axis_values, quantity, rate_percent, subtotal_incl_tax, subtotal_excl_tax)
SELECT line.order_id, line.line_no, variant.product_id, line.variant_id, line.product_code, line.variant_code, line.product_name,
       line.axis_values, line.quantity, line.rate_percent, line.subtotal, line.unit_price_excl_tax * line.quantity
FROM ordering.sales_order_line line
         LEFT JOIN catalog.variant variant ON variant.id = line.variant_id
ON CONFLICT (order_id, line_no) DO NOTHING;
