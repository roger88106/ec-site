package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.VariantId;
import lombok.NonNull;

import java.util.List;

/**
 * バリアントの並び順の変更コマンド
 *
 * @param productId  商品 ID
 * @param variantIds 並べたい順のバリアント ID（全て）
 * @param version    取得時の商品のバージョン
 */
public record ReorderVariantsCommand(@NonNull ProductId productId, @NonNull List<VariantId> variantIds, long version) {
}
