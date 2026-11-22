package com.ecsite.module.inventory.application.port;

import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 在庫のクエリリポジトリ
 */
public interface StockQueryRepository {

    /**
     * バリアントの在庫数を取得する。
     *
     * @param variantId バリアント ID
     * @return 在庫数。在庫が作られていなければ空
     */
    Optional<Integer> findQuantity(VariantId variantId);

    /**
     * バリアントの調整履歴を取得する。
     *
     * <p>調整日時の新しい順。同じ日時なら記録した順の新しい順。
     *
     * @param variantId バリアント ID
     * @param request   ページの指定
     * @return 調整履歴。在庫が作られていなければ空
     */
    Optional<PageResult<StockAdjustmentView>> findAdjustments(VariantId variantId, PageRequest request);

    /**
     * 商品ごとに在庫数を合計する。
     *
     * @param productIds 商品 ID
     * @return 商品 ID → 在庫数の合計。在庫がまだない商品は含まない
     */
    Map<ProductId, Long> sumQuantitiesByProduct(Collection<ProductId> productIds);

    /**
     * 販売可能数（在庫数 − 有効な引当の合計）を返す。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主。なければ null
     * @param now              現在時刻
     * @return バリアント ID → 販売可能数。在庫のないバリアントは含まない
     */
    Map<VariantId, Integer> findSellableQuantities(Collection<VariantId> variantIds, @Nullable ReservationHolderId excludedHolderId, Instant now);
}
