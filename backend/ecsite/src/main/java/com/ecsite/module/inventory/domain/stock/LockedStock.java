package com.ecsite.module.inventory.domain.stock;

import java.util.UUID;

/**
 * 行ロックして読み込んだ在庫。引当と注文による増減の判定に使う（docs/03-機能仕様/inventory.md「引当」）。
 *
 * @param stockItemId 在庫 ID
 * @param variantId   バリアント ID
 * @param quantity    在庫数
 */
public record LockedStock(UUID stockItemId, UUID variantId, int quantity) {
}
