-- 税抜価格に上限（9,999,999 円）を足す（商品の設計の文書「税抜価格」）
-- 税込価格と、カートの品目の小計の計算が long と bigint の範囲に収まるようにする。契約とドメインの上限と同じ値
ALTER TABLE catalog.product
    DROP CONSTRAINT product_price_excl_tax_check,
    ADD CONSTRAINT product_price_excl_tax_check CHECK (price_excl_tax BETWEEN 1 AND 9999999);
