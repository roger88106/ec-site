package com.ecsite.module.inventory.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.model.view.StockSummaryView;
import com.ecsite.module.inventory.application.port.StockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 在庫のクエリサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    /**
     * 在庫クエリリポジトリ
     */
    private final StockQueryRepository stockQueryRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * バリアントの在庫数を取得する。
     *
     * @param variantId バリアント ID
     * @return 在庫数
     */
    public int getQuantity(UUID variantId) {
        return stockQueryRepository.findQuantity(variantId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * バリアントの調整履歴を取得する。
     *
     * @param variantId バリアント ID
     * @param request   ページの指定
     * @return 調整履歴（新しい順）
     */
    public PageResult<StockAdjustmentView> listAdjustments(UUID variantId, PageRequest request) {
        return stockQueryRepository.findAdjustments(variantId, request)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 商品ごとの在庫数の合計を取得する。
     *
     * @param productIds 商品 ID
     * @return 指定した順の合計。在庫がまだない商品は 0
     */
    public List<StockSummaryView> listStockSummaries(List<UUID> productIds) {
        var totals = stockQueryRepository.sumQuantitiesByProduct(productIds);
        return productIds.stream()
                .map(productId -> new StockSummaryView(productId, totals.getOrDefault(productId, 0L)))
                .toList();
    }

    /**
     * 販売可能数を返す。在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（注文手続き中の自分の引当）。なければ null
     * @return バリアント ID → 販売可能数（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, @Nullable UUID excludedHolderId) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        var found = stockQueryRepository.findSellableQuantities(variantIds, excludedHolderId, clock.instant());
        var sellable = new LinkedHashMap<UUID, Integer>();
        variantIds.forEach(variantId -> sellable.put(variantId, found.getOrDefault(variantId, 0)));
        return sellable;
    }
}
