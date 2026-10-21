-- 商品とバリアント（docs/modules/catalog.md）
-- 商品の一部（軸、所属カテゴリ、バリアント、軸の値）は、商品と一緒に削除する

-- 商品
CREATE TABLE catalog.product
(
    id              uuid         NOT NULL,
    -- 商品品番。登録後は画面・API から変更できない（AC-15）
    product_code    varchar(32)  NOT NULL,
    name            varchar(200) NOT NULL,
    description     text         NOT NULL,
    -- 税モジュールの税区分。モジュールをまたぐので外部キーは張らない（constitution 6）
    tax_category_id uuid         NOT NULL,
    price_excl_tax  bigint       NOT NULL,
    -- UNPUBLISHED / PUBLISHED / DISCONTINUED
    status          varchar(20)  NOT NULL,
    -- 初回表示日時。null は一度も表示されていない
    first_shown_at  timestamptz,
    -- 公開期間。null は無限
    publish_start   timestamptz,
    publish_end     timestamptz,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT product_pkey PRIMARY KEY (id),
    -- 商品品番は全商品で一意（AC-14）
    CONSTRAINT product_product_code_key UNIQUE (product_code),
    -- 税抜価格は 1 円以上（AC-25）
    CONSTRAINT product_price_excl_tax_check CHECK (price_excl_tax >= 1),
    CONSTRAINT product_status_check CHECK (status IN ('UNPUBLISHED', 'PUBLISHED', 'DISCONTINUED')),
    CONSTRAINT product_publish_period_check CHECK (publish_start IS NULL OR publish_end IS NULL OR publish_start < publish_end)
);

-- バリアント軸（最大 3 つ。AC-13）
CREATE TABLE catalog.product_axis
(
    product_id uuid        NOT NULL,
    -- 軸の並び（1 始まり）
    position   smallint    NOT NULL,
    name       varchar(30) NOT NULL,
    CONSTRAINT product_axis_pkey PRIMARY KEY (product_id, position),
    CONSTRAINT product_axis_product_id_fkey FOREIGN KEY (product_id) REFERENCES catalog.product (id) ON DELETE CASCADE,
    CONSTRAINT product_axis_position_check CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT product_axis_product_id_name_key UNIQUE (product_id, name)
);

-- 所属カテゴリ
CREATE TABLE catalog.product_category
(
    product_id  uuid NOT NULL,
    category_id uuid NOT NULL,
    CONSTRAINT product_category_pkey PRIMARY KEY (product_id, category_id),
    CONSTRAINT product_category_product_id_fkey FOREIGN KEY (product_id) REFERENCES catalog.product (id) ON DELETE CASCADE,
    -- 商品が所属しているカテゴリは削除できない（AC-12）
    CONSTRAINT product_category_category_id_fkey FOREIGN KEY (category_id) REFERENCES catalog.category (id)
);

-- カテゴリの削除時に、所属する商品の有無を調べるため
CREATE INDEX product_category_category_id_idx ON catalog.product_category (category_id);

-- バリアント
CREATE TABLE catalog.variant
(
    id              uuid        NOT NULL,
    product_id      uuid        NOT NULL,
    -- バリアント品番。登録後は画面・API から変更できない（AC-21）
    variant_code    varchar(32) NOT NULL,
    -- 軸の値を U+001F で連結した値。軸のない商品では空文字
    axis_key        text        NOT NULL,
    -- 商品の中の表示順（小さいほど先）
    display_order   int         NOT NULL,
    sales_suspended boolean     NOT NULL DEFAULT false,
    -- 画像は US-7 で追加する
    image_id        uuid,
    CONSTRAINT variant_pkey PRIMARY KEY (id),
    CONSTRAINT variant_product_id_fkey FOREIGN KEY (product_id) REFERENCES catalog.product (id) ON DELETE CASCADE,
    -- バリアント品番は全バリアントで一意（AC-21）
    CONSTRAINT variant_variant_code_key UNIQUE (variant_code),
    -- 商品の中で軸の値の組み合わせは重複できない（AC-23）。軸のない商品はバリアントが 1 つだけ（AC-24）
    CONSTRAINT variant_product_id_axis_key_key UNIQUE (product_id, axis_key)
);

-- 軸の値
CREATE TABLE catalog.variant_axis_value
(
    variant_id uuid        NOT NULL,
    -- 商品の軸の並び（product_axis.position）と対応する
    position   smallint    NOT NULL,
    value      varchar(50) NOT NULL,
    CONSTRAINT variant_axis_value_pkey PRIMARY KEY (variant_id, position),
    CONSTRAINT variant_axis_value_variant_id_fkey FOREIGN KEY (variant_id) REFERENCES catalog.variant (id) ON DELETE CASCADE
);
