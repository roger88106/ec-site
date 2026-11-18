-- 商品品番とバリアント品番の照合順序を C にする（カタログモジュールの「商品の一覧」の品番の前方一致）
-- 照合順序が C でない DB では、前方一致（LIKE 'prefix%'）が一意制約の索引を使えず、テーブルを全件走査する。
-- 品番は英大文字・数字・ハイフンだけなので、並び順は変わらない。型と長さは変えない。
-- 一意制約 product_product_code_key と variant_variant_code_key の索引は、この変更で再作成される。この列を参照するビューと外部キーはない。

ALTER TABLE catalog.product ALTER COLUMN product_code TYPE varchar(32) COLLATE "C";
ALTER TABLE catalog.variant ALTER COLUMN variant_code TYPE varchar(32) COLLATE "C";
