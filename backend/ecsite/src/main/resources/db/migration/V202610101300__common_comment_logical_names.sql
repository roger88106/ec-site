-- テーブルとカラムに論理名を付ける（constitution 7.1。論理名は用語集に合わせる）
-- 以降のマイグレーションでは、テーブルを作るときに論理名も付ける

-- イベント発行記録
COMMENT ON TABLE common.event_publication IS 'イベント発行記録';
COMMENT ON COLUMN common.event_publication.id IS 'イベント発行記録 ID';
COMMENT ON COLUMN common.event_publication.listener_id IS 'リスナー ID';
COMMENT ON COLUMN common.event_publication.event_type IS 'イベントの型';
COMMENT ON COLUMN common.event_publication.serialized_event IS '直列化したイベント';
COMMENT ON COLUMN common.event_publication.publication_date IS '発行日時';
COMMENT ON COLUMN common.event_publication.completion_date IS '完了日時';
COMMENT ON COLUMN common.event_publication.status IS '状態';
COMMENT ON COLUMN common.event_publication.completion_attempts IS '処理の試行回数';
COMMENT ON COLUMN common.event_publication.last_resubmission_date IS '最終再発行日時';

-- 税区分
COMMENT ON TABLE tax.tax_category IS '税区分';
COMMENT ON COLUMN tax.tax_category.id IS '税区分 ID';
COMMENT ON COLUMN tax.tax_category.name IS '名称';
COMMENT ON COLUMN tax.tax_category.version IS 'バージョン';
COMMENT ON COLUMN tax.tax_category.created_at IS '作成日時';
COMMENT ON COLUMN tax.tax_category.updated_at IS '更新日時';

-- 税率
COMMENT ON TABLE tax.tax_rate IS '税率';
COMMENT ON COLUMN tax.tax_rate.id IS '税率 ID';
COMMENT ON COLUMN tax.tax_rate.tax_category_id IS '税区分 ID';
COMMENT ON COLUMN tax.tax_rate.rate_percent IS '率';
COMMENT ON COLUMN tax.tax_rate.effective_from IS '適用開始日時';
COMMENT ON COLUMN tax.tax_rate.effective_to IS '適用終了日時';

-- カテゴリ
COMMENT ON TABLE catalog.category IS 'カテゴリ';
COMMENT ON COLUMN catalog.category.id IS 'カテゴリ ID';
COMMENT ON COLUMN catalog.category.parent_id IS '親カテゴリ ID';
COMMENT ON COLUMN catalog.category.name IS '名称';
COMMENT ON COLUMN catalog.category.depth IS '階層の深さ';
COMMENT ON COLUMN catalog.category.display_order IS '表示順';
COMMENT ON COLUMN catalog.category.version IS 'バージョン';
COMMENT ON COLUMN catalog.category.created_at IS '作成日時';
COMMENT ON COLUMN catalog.category.updated_at IS '更新日時';

-- 商品
COMMENT ON TABLE catalog.product IS '商品';
COMMENT ON COLUMN catalog.product.id IS '商品 ID';
COMMENT ON COLUMN catalog.product.product_code IS '商品品番';
COMMENT ON COLUMN catalog.product.name IS '商品名';
COMMENT ON COLUMN catalog.product.description IS '説明';
COMMENT ON COLUMN catalog.product.tax_category_id IS '税区分 ID';
COMMENT ON COLUMN catalog.product.price_excl_tax IS '税抜価格';
COMMENT ON COLUMN catalog.product.status IS '公開状態';
COMMENT ON COLUMN catalog.product.first_shown_at IS '初回表示日時';
COMMENT ON COLUMN catalog.product.publish_start IS '公開開始日時';
COMMENT ON COLUMN catalog.product.publish_end IS '公開終了日時';
COMMENT ON COLUMN catalog.product.version IS 'バージョン';
COMMENT ON COLUMN catalog.product.created_at IS '作成日時';
COMMENT ON COLUMN catalog.product.updated_at IS '更新日時';

-- バリアント軸
COMMENT ON TABLE catalog.product_axis IS 'バリアント軸';
COMMENT ON COLUMN catalog.product_axis.product_id IS '商品 ID';
COMMENT ON COLUMN catalog.product_axis.position IS '軸の位置';
COMMENT ON COLUMN catalog.product_axis.name IS '軸の名称';

-- 所属カテゴリ
COMMENT ON TABLE catalog.product_category IS '所属カテゴリ';
COMMENT ON COLUMN catalog.product_category.product_id IS '商品 ID';
COMMENT ON COLUMN catalog.product_category.category_id IS 'カテゴリ ID';

-- バリアント
COMMENT ON TABLE catalog.variant IS 'バリアント';
COMMENT ON COLUMN catalog.variant.id IS 'バリアント ID';
COMMENT ON COLUMN catalog.variant.product_id IS '商品 ID';
COMMENT ON COLUMN catalog.variant.variant_code IS 'バリアント品番';
COMMENT ON COLUMN catalog.variant.axis_key IS '軸キー';
COMMENT ON COLUMN catalog.variant.display_order IS '表示順';
COMMENT ON COLUMN catalog.variant.sales_suspended IS '販売停止';
COMMENT ON COLUMN catalog.variant.image_id IS '商品画像 ID';

-- 軸の値
COMMENT ON TABLE catalog.variant_axis_value IS '軸の値';
COMMENT ON COLUMN catalog.variant_axis_value.variant_id IS 'バリアント ID';
COMMENT ON COLUMN catalog.variant_axis_value.position IS '軸の位置';
COMMENT ON COLUMN catalog.variant_axis_value.value IS '値';

-- 商品画像
COMMENT ON TABLE catalog.product_image IS '商品画像';
COMMENT ON COLUMN catalog.product_image.id IS '商品画像 ID';
COMMENT ON COLUMN catalog.product_image.product_id IS '商品 ID';
COMMENT ON COLUMN catalog.product_image.display_order IS '表示順';
COMMENT ON COLUMN catalog.product_image.created_at IS '作成日時';
COMMENT ON COLUMN catalog.product_image.asset_id IS 'アセット ID';

-- 在庫
COMMENT ON TABLE inventory.stock_item IS '在庫';
COMMENT ON COLUMN inventory.stock_item.id IS '在庫 ID';
COMMENT ON COLUMN inventory.stock_item.variant_id IS 'バリアント ID';
COMMENT ON COLUMN inventory.stock_item.product_id IS '商品 ID';
COMMENT ON COLUMN inventory.stock_item.quantity IS '在庫数';
COMMENT ON COLUMN inventory.stock_item.created_at IS '作成日時';
COMMENT ON COLUMN inventory.stock_item.updated_at IS '更新日時';

-- 在庫調整
COMMENT ON TABLE inventory.stock_adjustment IS '在庫調整';
COMMENT ON COLUMN inventory.stock_adjustment.id IS '在庫調整 ID';
COMMENT ON COLUMN inventory.stock_adjustment.sequence_no IS '連番';
COMMENT ON COLUMN inventory.stock_adjustment.stock_item_id IS '在庫 ID';
COMMENT ON COLUMN inventory.stock_adjustment.delta IS '増減量';
COMMENT ON COLUMN inventory.stock_adjustment.reason IS '調整理由';
COMMENT ON COLUMN inventory.stock_adjustment.memo IS 'メモ';
COMMENT ON COLUMN inventory.stock_adjustment.quantity_after IS '調整後の在庫数';
COMMENT ON COLUMN inventory.stock_adjustment.actor_id IS '操作者 ID';
COMMENT ON COLUMN inventory.stock_adjustment.adjusted_at IS '調整日時';

-- アセット
COMMENT ON TABLE media.asset IS 'アセット';
COMMENT ON COLUMN media.asset.id IS 'アセット ID';
COMMENT ON COLUMN media.asset.format IS '形式';
COMMENT ON COLUMN media.asset.storage_key IS '配信のキー';
COMMENT ON COLUMN media.asset.created_at IS '作成日時';
