-- 注文番号の照合順序を C にする（注文モジュールの「注文の一覧」の注文番号の前方一致）
-- 照合順序が C でない DB では、前方一致（LIKE 'prefix%'）が一意制約の索引を使えず、テーブルを全件走査する。
-- 注文番号は数字とハイフンだけなので、並び順は変わらない。型と長さは変えない。
-- 一意制約 sales_order_order_number_key の索引は、この変更で再作成される。この列を参照するビューはない。

ALTER TABLE ordering.sales_order ALTER COLUMN order_number TYPE varchar(15) COLLATE "C";
