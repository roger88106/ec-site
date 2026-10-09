package com.ecsite.module.inventory.domain.stock.port;

import java.util.UUID;

/**
 * 在庫のリポジトリ。<br />
 * 在庫数の更新は読み込んで書き戻さず、DB 上の差分の加算で行う（docs/modules/inventory.md）。
 */
public interface StockItemRepository {

    /**
     * 在庫数 0 の在庫を作る（AC-41）。すでにあれば何もしない（同じイベントが 2 回届いても 1 つ）。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    void createIfAbsent(UUID variantId, UUID productId);

    /**
     * バリアントの在庫と調整履歴を消す。なければ何もしない。
     *
     * @param variantId バリアント ID
     */
    void deleteByVariantId(UUID variantId);

    /**
     * 商品のすべての在庫と調整履歴を消す。なければ何もしない。
     *
     * @param productId 商品 ID
     */
    void deleteByProductId(UUID productId);
}
