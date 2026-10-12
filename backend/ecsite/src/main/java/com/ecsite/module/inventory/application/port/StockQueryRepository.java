package com.ecsite.module.inventory.application.port;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;

import java.time.Instant;
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


    /**
     * 販売可能数（在庫数 − 有効な引当の合計）を返す（spec 002 BR-11）。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 販売可能数から除かない（数えない）引当の持ち主。なければ null
     * @param now              現在時刻
     * @return バリアント ID → 販売可能数。在庫のないバリアントは含まない
     */
    Map<UUID, Integer> findSellableQuantities(Collection<UUID> variantIds, UUID excludedHolderId, Instant now);
}
