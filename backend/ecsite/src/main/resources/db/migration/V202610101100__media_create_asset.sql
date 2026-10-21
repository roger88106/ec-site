-- アセット（docs/modules/media.md「アセット」、plan 001 第 7 節）
-- ファイルはオブジェクトストレージの配信のバケットに置き、ここにはキーだけを持つ
CREATE SCHEMA IF NOT EXISTS media;

CREATE TABLE media.asset
(
    id          uuid         NOT NULL,
    -- 形式（JPEG、PNG、WEBP）。確定のときに、ファイルの先頭のバイトで判定したもの
    format      varchar(20)  NOT NULL,
    -- 配信のバケットのキー（assets/{assetId}.{拡張子}）。上書きしない
    storage_key varchar(300) NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT asset_pkey PRIMARY KEY (id),
    CONSTRAINT asset_storage_key_key UNIQUE (storage_key)
);
