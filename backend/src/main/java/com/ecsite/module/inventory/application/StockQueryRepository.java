package com.ecsite.module.inventory.application;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 在庫のクエリリポジトリ
 */
public interface StockQueryRepository {

    /**
     * バリアントの在庫数を取得する。
     *
     * @param variantId バリアント ID
     * @return 在庫数。在庫がまだなければ空
     */
    Optional<Integer> findQuantity(UUID variantId);

    /**
     * バリアントの調整履歴を取得する（AC-45）。<br />
     * 調整日時の新しい順。同じ日時なら記録した順の新しい順（docs/modules/inventory.md）。
     *
     * @param variantId バリアント ID
     * @param request   ページの指定
     * @return 調整履歴。在庫がまだなければ空
     */
    Optional<PageResult<StockAdjustmentView>> findAdjustments(UUID variantId, PageRequest request);

    /**
     * 商品ごとに在庫数を合計する（AC-49）。
     *
     * @param productIds 商品 ID
     * @return 商品 ID → 在庫数の合計。在庫がまだない商品は含まない
     */
    Map<UUID, Long> sumQuantitiesByProduct(Collection<UUID> productIds);
}
