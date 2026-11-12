package com.ecsite.module.catalog.application.model.command;

import lombok.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * バリアントの並び順の変更コマンド
 *
 * @param productId  商品 ID
 * @param variantIds 並べたい順のバリアント ID（全て）
 * @param version    取得時の商品のバージョン
 */
public record ReorderVariantsCommand(@NonNull UUID productId, @NonNull List<UUID> variantIds, long version) {
}
