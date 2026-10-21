-- 商品画像（docs/modules/catalog.md「商品画像」、plan 001 第 7 節）
-- ファイルはオブジェクトストレージの配信のバケットに置き、ここにはキーだけを持つ

CREATE TABLE catalog.product_image
(
    id            uuid         NOT NULL,
    product_id    uuid         NOT NULL,
    -- 配信のバケットのキー（products/{productId}/{imageId}.{拡張子}）。上書きしない
    storage_key   varchar(300) NOT NULL,
    -- 商品の中の表示順（小さいほど先。先頭が代表画像。AC-37）
    display_order int          NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT product_image_pkey PRIMARY KEY (id),
    CONSTRAINT product_image_product_id_fkey FOREIGN KEY (product_id) REFERENCES catalog.product (id) ON DELETE CASCADE,
    CONSTRAINT product_image_storage_key_key UNIQUE (storage_key),
    -- バリアントから、同じ商品の画像だけを指せるようにするため
    CONSTRAINT product_image_product_id_id_key UNIQUE (product_id, id)
);

-- バリアントの画像は、同じ商品の画像だけ（AC-38）。画像を削除したら、バリアントの指定だけを外す
ALTER TABLE catalog.variant
    ADD CONSTRAINT variant_image_id_fkey FOREIGN KEY (product_id, image_id)
        REFERENCES catalog.product_image (product_id, id) ON DELETE SET NULL (image_id);
