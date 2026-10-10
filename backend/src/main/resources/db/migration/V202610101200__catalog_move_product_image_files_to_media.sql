-- 商品画像のファイルを、メディアモジュールのアセットに移す（docs/modules/media.md、catalog.md「商品画像」）
-- 登録済みの画像は、アセット ID を画像 ID と同じにし、配信のキーはそのまま使う（ファイルは動かさない）

INSERT INTO media.asset (id, format, storage_key, created_at)
SELECT id,
       CASE substring(storage_key FROM '\.([a-z]+)$')
           WHEN 'jpg' THEN 'JPEG'
           WHEN 'png' THEN 'PNG'
           WHEN 'webp' THEN 'WEBP'
           END,
       storage_key,
       created_at
FROM catalog.product_image;

ALTER TABLE catalog.product_image
    -- メディアモジュールのアセット ID。モジュールをまたぐので外部キーは張らない（constitution 6）
    ADD COLUMN asset_id uuid;

UPDATE catalog.product_image
SET asset_id = id;

ALTER TABLE catalog.product_image
    ALTER COLUMN asset_id SET NOT NULL,
    -- アセットは 1 つの画像だけが持つ（docs/modules/media.md）
    ADD CONSTRAINT product_image_asset_id_key UNIQUE (asset_id),
    DROP COLUMN storage_key;
