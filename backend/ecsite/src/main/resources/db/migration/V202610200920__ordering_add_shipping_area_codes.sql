-- 注文のお届け先の都道府県と市区町村を、コードと注文の時点の名前で持つ（docs/backend/modules/order.md、spec 016 AC-258、261）
-- 既存の注文は、都道府県名からコードを引き、住所の先頭のいちばん長い市区町村名を市区町村として住所から分ける。
-- 照らせない注文があれば、注文番号を示して失敗させる。
--
-- 規則の例外：他のモジュールの表を読まない規則（バックエンドのアーキテクチャ 第 3 節）の例外として、この移行だけ地域の表を読む。
-- 既存の注文をマスターデータと照らすには、両方の表を同じ所で読むしかないため。この後、注文は地域の表を読まない（plan 016 第 2.3 節）。

ALTER TABLE ordering.sales_order RENAME COLUMN prefecture TO prefecture_name;
ALTER TABLE ordering.sales_order ALTER COLUMN prefecture_name TYPE varchar(10);
ALTER TABLE ordering.sales_order ADD COLUMN prefecture_code char(2);
ALTER TABLE ordering.sales_order ADD COLUMN municipality_code char(5);
ALTER TABLE ordering.sales_order ADD COLUMN municipality_name varchar(30);

UPDATE ordering.sales_order o
SET prefecture_code = p.code
FROM region.prefecture p
WHERE p.name = o.prefecture_name;

UPDATE ordering.sales_order o
SET municipality_code = m.code,
    municipality_name = m.name,
    address           = ltrim(substr(o.address, length(m.name) + 1))
FROM (SELECT DISTINCT ON (o2.id) o2.id, m2.code, m2.name
      FROM ordering.sales_order o2
               JOIN region.municipality m2
                    ON m2.prefecture_code = o2.prefecture_code AND starts_with(o2.address, m2.name)
      ORDER BY o2.id, length(m2.name) DESC) AS m
WHERE m.id = o.id;

DO
$$
    DECLARE
        unmatched text;
    BEGIN
        SELECT string_agg(order_number, '、' ORDER BY order_number)
        INTO unmatched
        FROM ordering.sales_order
        WHERE prefecture_code IS NULL
           OR municipality_code IS NULL
           OR address = '';
        IF unmatched IS NOT NULL THEN
            RAISE EXCEPTION 'お届け先の都道府県・市区町村をマスターデータと照らせない注文があります：%', unmatched;
        END IF;
    END
$$;

ALTER TABLE ordering.sales_order ALTER COLUMN prefecture_code SET NOT NULL;
ALTER TABLE ordering.sales_order ALTER COLUMN municipality_code SET NOT NULL;
ALTER TABLE ordering.sales_order ALTER COLUMN municipality_name SET NOT NULL;
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_prefecture_code_check CHECK (prefecture_code ~ '^[0-9]{2}$');
ALTER TABLE ordering.sales_order ADD CONSTRAINT sales_order_municipality_code_check
    CHECK (municipality_code ~ '^[0-9]{5}$' AND left(municipality_code, 2) = prefecture_code);

COMMENT ON COLUMN ordering.sales_order.prefecture_code IS '都道府県のコード';
COMMENT ON COLUMN ordering.sales_order.prefecture_name IS '都道府県名（注文の時点の写し）';
COMMENT ON COLUMN ordering.sales_order.municipality_code IS '市区町村のコード';
COMMENT ON COLUMN ordering.sales_order.municipality_name IS '市区町村名（注文の時点の写し）';
COMMENT ON COLUMN ordering.sales_order.address IS '市区町村より下の住所';
