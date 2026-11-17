package com.ecsite.module.inventory.domain.stock;

import java.util.UUID;

/**
 * 行ロックして読み込んだ在庫
 *
 * <p>在庫は集約のクラスを持たない（在庫数を DB の差分の加算で変えるため）。引当の判定のために、ロックした在庫の行を読み取り専用でコピーする。
 * 在庫 ID で識別するので、値オブジェクトではない。在庫数が負でないことは、DB の CHECK 制約で守る。
 *
 * @param stockItemId 在庫 ID
 * @param variantId   バリアント ID
 * @param quantity    在庫数
 */
public record LockedStock(UUID stockItemId, UUID variantId, int quantity) {
}
