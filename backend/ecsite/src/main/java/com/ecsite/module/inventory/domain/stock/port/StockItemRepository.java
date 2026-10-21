package com.ecsite.module.inventory.domain.stock.port;

import com.ecsite.module.inventory.domain.stock.StockAdjustment;

import java.util.UUID;

/**
 * 在庫のリポジトリ。<br />
 * 在庫数の更新は読み込んで書き戻さず、DB 上の差分の加算で行う（docs/backend/modules/inventory.md）。
 */
public interface StockItemRepository {

    /**
     * 在庫数 0 の在庫を作る。すでにあれば何もしない（同じイベントが 2 回届いても 1 つ）。
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

    /**
     * 在庫数に増減量を加え、調整履歴を記録する。<br />
     * 「調整後に負にならない場合だけ加える」を 1 回の更新で行うので、同時の調整もすべて反映される。
     * 調整後に負になるなら {@code INSUFFICIENT_STOCK}、在庫がなければ {@code NOT_FOUND} の業務のエラーを投げる。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 調整後の在庫数
     */
    int adjust(UUID variantId, StockAdjustment adjustment);
}
