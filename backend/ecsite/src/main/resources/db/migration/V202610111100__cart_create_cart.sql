-- カートとカートの品目（docs/modules/cart.md、spec 002 US-14、15）
-- バリアントは商品カタログモジュールの ID なので、外部キーは張らない（constitution 6）
CREATE SCHEMA IF NOT EXISTS cart;

-- カート。最終更新から 30 日たったものは、読むときに空として扱う（AC-81）
CREATE TABLE cart.cart
(
    id         uuid        NOT NULL,
    -- 変更のたびに上げる。注文手続きを始めた後の変更を見つけるため
    version    bigint      NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT cart_pkey PRIMARY KEY (id)
);

-- カートの品目
CREATE TABLE cart.cart_item
(
    cart_id     uuid        NOT NULL,
    variant_id  uuid        NOT NULL,
    quantity    int         NOT NULL,
    -- 入れた順に並べるための連番（同じ時刻に入れた品目の順も決まる）
    sequence_no bigint      NOT NULL GENERATED ALWAYS AS IDENTITY,
    added_at    timestamptz NOT NULL,
    -- 同じバリアントは 1 品目にまとめる（AC-74）
    CONSTRAINT cart_item_pkey PRIMARY KEY (cart_id, variant_id),
    CONSTRAINT cart_item_cart_id_fkey FOREIGN KEY (cart_id) REFERENCES cart.cart (id) ON DELETE CASCADE,
    -- 1 品目の数量は 1〜10（AC-75）
    CONSTRAINT cart_item_quantity_check CHECK (quantity BETWEEN 1 AND 10)
);

COMMENT ON TABLE cart.cart IS 'カート';
COMMENT ON COLUMN cart.cart.id IS 'カート ID';
COMMENT ON COLUMN cart.cart.version IS 'バージョン';
COMMENT ON COLUMN cart.cart.created_at IS '作成日時';
COMMENT ON COLUMN cart.cart.updated_at IS '更新日時';

COMMENT ON TABLE cart.cart_item IS 'カートの品目';
COMMENT ON COLUMN cart.cart_item.cart_id IS 'カート ID';
COMMENT ON COLUMN cart.cart_item.variant_id IS 'バリアント ID';
COMMENT ON COLUMN cart.cart_item.quantity IS '数量';
COMMENT ON COLUMN cart.cart_item.sequence_no IS '連番';
COMMENT ON COLUMN cart.cart_item.added_at IS '入れた日時';
