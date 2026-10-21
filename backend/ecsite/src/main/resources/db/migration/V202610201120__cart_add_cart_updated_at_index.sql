-- カートの片付け（spec 017 AC-284、docs/design/modules/cart.md「期限」）
-- 片付けのジョブが、最後の変更から期限と保存期間を過ぎたカートを探すため
CREATE INDEX cart_updated_at_idx ON cart.cart (updated_at);
