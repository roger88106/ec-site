-- 出荷のローカル用 seed
-- レポート用の注文（R__ordering_seed.sql）の出荷。出荷済みの注文と、返送でキャンセルした注文（キャンセルの理由で見分ける）に作る。
-- ID は 00000000-0000-0000-0013-<注文の番号 12 桁>。出荷は注文日時の 1 日後（注文の seed の日時の筋）。
-- 配送状態の筋（S は出荷の日時。写しの seed は、この筋で状態の履歴を作る）：
--   出荷済み S → 配送中 S + 6 時間 → 配達完了 S + 1 日〜2 日（12 時間きざみ）。返送は S + 4 日（配送中から）
-- seed の「今」は 2026-10-01 0 時（東京）とし、それより後に進むはずの出荷は、そこで止める（配送中か出荷済み）。
-- 出荷は物流サービスの通知で変わるので、seed の再実行で上書きしない

INSERT INTO shipping.shipment (id, order_id, carrier, tracking_number, status, shipped_at, last_notified_at, created_at, updated_at)
SELECT ('00000000-0000-0000-0013-' || right(o.id::text, 12))::uuid,
       o.id,
       (ARRAY ['YAMATO', 'SAGAWA', 'JAPAN_POST'])[right(o.id::text, 12)::int % 3 + 1],
       '4' || right(o.id::text, 11),
       delivery.status,
       delivery.shipped_at,
       delivery.notified_at,
       delivery.shipped_at,
       coalesce(delivery.notified_at, delivery.shipped_at)
FROM ordering.sales_order o
         CROSS JOIN LATERAL (SELECT o.ordered_at + interval '1 day' AS shipped_at,
                                    o.cancellation_reason = '受取拒否により返送されたため' AS returned,
                                    o.ordered_at + interval '2 days' + (right(o.id::text, 12)::int % 3) * interval '12 hours' AS delivered_at,
                                    timestamptz '2026-10-01 00:00:00+09' AS seed_now) AS plan
         CROSS JOIN LATERAL (SELECT plan.shipped_at,
                                    CASE WHEN plan.returned THEN 'RETURNED'
                                         WHEN plan.delivered_at <= plan.seed_now THEN 'DELIVERED'
                                         WHEN plan.shipped_at + interval '6 hours' <= plan.seed_now THEN 'IN_TRANSIT'
                                         ELSE 'SHIPPED' END AS status,
                                    CASE WHEN plan.returned THEN plan.shipped_at + interval '4 days'
                                         WHEN plan.delivered_at <= plan.seed_now THEN plan.delivered_at
                                         WHEN plan.shipped_at + interval '6 hours' <= plan.seed_now THEN plan.shipped_at + interval '6 hours'
                                         END AS notified_at) AS delivery
WHERE o.id::text LIKE '00000000-0000-0000-0010-%'
  AND (o.status = 'SHIPPED' OR plan.returned)
ON CONFLICT (order_id) DO NOTHING;
