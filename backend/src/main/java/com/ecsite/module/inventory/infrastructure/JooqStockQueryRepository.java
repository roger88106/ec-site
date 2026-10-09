package com.ecsite.module.inventory.infrastructure;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.StockQueryRepository;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;

/**
 * 在庫のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqStockQueryRepository implements StockQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * バリアントの在庫数を取得する。
     *
     * @param variantId バリアント ID
     * @return 在庫数
     */
    @Override
    public Optional<Integer> findQuantity(UUID variantId) {
        return dsl.select(STOCK_ITEM.QUANTITY)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                .fetchOptional(STOCK_ITEM.QUANTITY);
    }

    /**
     * バリアントの調整履歴を取得する。
     *
     * @param variantId バリアント ID
     * @param request   ページの指定
     * @return 調整履歴
     */
    @Override
    public Optional<PageResult<StockAdjustmentView>> findAdjustments(UUID variantId, PageRequest request) {
        return dsl.select(STOCK_ITEM.ID)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                .fetchOptional(STOCK_ITEM.ID)
                .map(stockItemId -> {
                    var condition = STOCK_ADJUSTMENT.STOCK_ITEM_ID.eq(stockItemId);
                    var items = dsl.selectFrom(STOCK_ADJUSTMENT)
                            .where(condition)
                            .orderBy(STOCK_ADJUSTMENT.ADJUSTED_AT.desc(), STOCK_ADJUSTMENT.SEQUENCE_NO.desc())
                            .limit(request.size())
                            .offset(request.offset())
                            .fetch(record -> new StockAdjustmentView(
                                    record.getId(),
                                    record.getDelta(),
                                    record.getReason(),
                                    record.getMemo(),
                                    record.getQuantityAfter(),
                                    record.getAdjustedAt()));
                    return PageResult.of(items, request, dsl.fetchCount(STOCK_ADJUSTMENT, condition));
                });
    }

    /**
     * 商品ごとに在庫数を合計する。
     *
     * @param productIds 商品 ID
     * @return 商品 ID → 在庫数の合計
     */
    @Override
    public Map<UUID, Long> sumQuantitiesByProduct(Collection<UUID> productIds) {
        var total = DSL.sum(STOCK_ITEM.QUANTITY).cast(Long.class);
        return dsl.select(STOCK_ITEM.PRODUCT_ID, total)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.PRODUCT_ID.in(productIds))
                .groupBy(STOCK_ITEM.PRODUCT_ID)
                .fetchMap(STOCK_ITEM.PRODUCT_ID, total);
    }
}
