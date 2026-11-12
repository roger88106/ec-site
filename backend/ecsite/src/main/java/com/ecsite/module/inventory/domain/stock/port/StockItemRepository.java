package com.ecsite.module.inventory.domain.stock.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;

import java.util.UUID;

/**
 * 在庫のリポジトリ
 *
 * <p>在庫数は読み込んで書き戻さず、DB 上の差分の加算で更新する。
 */
public interface StockItemRepository {

    /**
     * 在庫数 0 の在庫を作る。
     *
     * <p>すでにあれば何もしない。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    void createIfAbsent(UUID variantId, UUID productId);

    /**
     * バリアントの在庫と調整履歴を消す。
     *
     * <p>なければ何もしない。
     *
     * @param variantId バリアント ID
     */
    void deleteByVariantId(UUID variantId);

    /**
     * 商品のすべての在庫と調整履歴を消す。
     *
     * <p>なければ何もしない。
     *
     * @param productId 商品 ID
     */
    void deleteByProductId(UUID productId);

    /**
     * 在庫数に増減量を加え、調整履歴を記録する。
     *
     * <p>「調整後に負にならない場合だけ加える」を 1 回の更新で行うので、同時の調整もすべて反映される。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 調整後の在庫数
     * @throws BusinessException 調整後に負になる（{@code INSUFFICIENT_STOCK}）
     * @throws BusinessException 運営者の調整で在庫数が上限を超える（{@code STOCK_QUANTITY_EXCEEDED}）
     * @throws BusinessException 在庫がない（{@code NOT_FOUND}）
     */
    int adjust(UUID variantId, StockAdjustment adjustment);
}
