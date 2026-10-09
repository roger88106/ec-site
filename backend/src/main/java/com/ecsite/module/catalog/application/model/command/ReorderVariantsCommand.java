package com.ecsite.module.catalog.application.model.command;

import java.util.List;
import java.util.UUID;

/**
 * バリアントの並び順の変更コマンド
 *
 * @param productId  商品 ID
 * @param variantIds 並べたい順のバリアント ID（すべて）
 * @param version    取得時の商品のバージョン
 */
public record ReorderVariantsCommand(UUID productId, List<UUID> variantIds, long version) {
}
