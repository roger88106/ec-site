-- 在庫と在庫調整（docs/modules/inventory.md）
-- バリアントと商品は商品カタログモジュールの ID なので、外部キーは張らない（constitution 6）

-- 在庫
CREATE TABLE inventory.stock_item
(
    id         uuid        NOT NULL,
    variant_id uuid        NOT NULL,
    -- B 側で商品ごとの在庫数の合計を出すため
    product_id uuid        NOT NULL,
    quantity   int         NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT stock_item_pkey PRIMARY KEY (id),
    -- 同じバリアント登録のイベントが 2 回届いても、在庫は 1 つ
    CONSTRAINT stock_item_variant_id_key UNIQUE (variant_id),
    -- 在庫数は負にならない（AC-43）
    CONSTRAINT stock_item_quantity_check CHECK (quantity >= 0)
);

CREATE INDEX stock_item_product_id_idx ON inventory.stock_item (product_id);

-- 在庫調整（履歴。追記するだけ）
CREATE TABLE inventory.stock_adjustment
(
    id             uuid        NOT NULL,
    -- 同じ日時の調整の順序
    sequence_no    bigint      NOT NULL GENERATED ALWAYS AS IDENTITY,
    stock_item_id  uuid        NOT NULL,
    delta          int         NOT NULL,
    -- ARRIVAL / STOCKTAKE / DAMAGE_OR_LOSS / OTHER（AC-46）
    reason         varchar(20) NOT NULL,
    memo           varchar(500),
    -- 調整後の在庫数（AC-45）
    quantity_after int         NOT NULL,
    -- 認証の導入までは null
    actor_id       uuid,
    adjusted_at    timestamptz NOT NULL,
    CONSTRAINT stock_adjustment_pkey PRIMARY KEY (id),
    CONSTRAINT stock_adjustment_stock_item_id_fkey FOREIGN KEY (stock_item_id) REFERENCES inventory.stock_item (id) ON DELETE CASCADE,
    CONSTRAINT stock_adjustment_delta_check CHECK (delta <> 0),
    CONSTRAINT stock_adjustment_reason_check CHECK (reason IN ('ARRIVAL', 'STOCKTAKE', 'DAMAGE_OR_LOSS', 'OTHER')),
    CONSTRAINT stock_adjustment_quantity_after_check CHECK (quantity_after >= 0)
);

-- 履歴を新しい順に取得するため（AC-45）
CREATE INDEX stock_adjustment_stock_item_id_adjusted_at_idx
    ON inventory.stock_adjustment (stock_item_id, adjusted_at DESC, sequence_no DESC);
