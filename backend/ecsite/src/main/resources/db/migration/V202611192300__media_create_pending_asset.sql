-- 仮のアセット（docs/03-機能仕様/media.md「アップロードの流れ」）
-- 用意で配信のバケットに書く前に記録し、登録か破棄で消す。残ったものは、保存期間を過ぎたら片付けのジョブが
-- 配信のファイルと共に消す（参照されない公開のファイルを残さないため）。
-- 表を加えるだけなので、前のリリースのアプリは、この表を読み書きせずに動き続ける。
CREATE TABLE media.pending_asset
(
    -- アセット ID。登録すると、同じ ID のアセットになる
    id          uuid         NOT NULL,
    -- 配信のバケットのキー（assets/{assetId}.{拡張子}）
    storage_key varchar(300) NOT NULL,
    -- 用意した時刻。保存期間の起点
    prepared_at timestamptz  NOT NULL,
    CONSTRAINT pending_asset_pkey PRIMARY KEY (id),
    CONSTRAINT pending_asset_storage_key_key UNIQUE (storage_key)
);

-- 片付けのジョブが、保存期間を過ぎたものを用意した時刻の古い順（同じ時刻はアセット ID の順）に選ぶため
CREATE INDEX pending_asset_prepared_at_id_idx ON media.pending_asset (prepared_at, id);

COMMENT ON TABLE media.pending_asset IS '仮のアセット';
COMMENT ON COLUMN media.pending_asset.id IS 'アセット ID';
COMMENT ON COLUMN media.pending_asset.storage_key IS '配信のキー';
COMMENT ON COLUMN media.pending_asset.prepared_at IS '用意日時';
