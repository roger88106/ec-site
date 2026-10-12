-- 会員のカート（docs/modules/cart.md、spec 003 AC-121）
-- 会員は会員モジュールの ID なので、外部キーは張らない（constitution 6）
-- ログインせずに作ったカートは会員を持たない（null）
ALTER TABLE cart.cart
    ADD COLUMN member_id uuid;

-- 会員のカートは会員ごとに 1 つ（同じ会員の初回の要求が同時に来ても 1 つ）
ALTER TABLE cart.cart
    ADD CONSTRAINT cart_member_id_key UNIQUE (member_id);

COMMENT ON COLUMN cart.cart.member_id IS '会員 ID';
