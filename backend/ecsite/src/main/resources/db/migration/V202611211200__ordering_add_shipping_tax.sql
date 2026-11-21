-- 注文に、送料の消費税の列を追加する（spec 484）
-- 注文の成立のときに、送料だけを課税対象として求めた額を保存する。設定の送料の税区分や税率のマスターを変えても、過去の注文の額が変わらないようにする。
-- 列を追加する前の注文は空（null）とし、埋めない。埋めるには税のモジュールの表を読む必要があるため。空の注文は、読むときに注文日時の税率から求める。
-- 列の追加だけなので、前のリリースのアプリもそのまま動く。
ALTER TABLE ordering.sales_order
    ADD COLUMN shipping_tax bigint;

COMMENT ON COLUMN ordering.sales_order.shipping_tax IS '送料の消費税（注文の成立のときの額。列を追加する前の注文は持たない）';
