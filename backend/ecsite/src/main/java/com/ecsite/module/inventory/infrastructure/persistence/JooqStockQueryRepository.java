package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.application.port.StockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;

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
                // 在庫がなければ（そのバリアントがなければ）空。あれば、その在庫の調整履歴を 1 ページ分取得する
                .map(stockItemId -> {
                    var condition = STOCK_ADJUSTMENT.STOCK_ITEM_ID.eq(stockItemId);
                    var items = dsl.selectFrom(STOCK_ADJUSTMENT)
                            .where(condition)
                            // 同じ時刻の調整は、記録した順（連番）の新しいほうを先にする
                            .orderBy(STOCK_ADJUSTMENT.ADJUSTED_AT.desc(), STOCK_ADJUSTMENT.SEQUENCE_NO.desc())
                            .limit(request.size())
                            .offset(request.offset())
                            .fetch(record -> new StockAdjustmentView(
                                    record.getId(),
                                    record.getDelta(),
                                    record.getReason(),
                                    record.getMemo(),
                                    record.getOrderId(),
                                    record.getOrderNumber(),
                                    record.getQuantityAfter(),
                                    record.getAdjustedAt(),
                                    record.getActorName()));
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


    /**
     * 販売可能数を返す。在庫数から、在庫ごとの有効な引当の合計を引く。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主
     * @param now              現在時刻
     * @return バリアント ID → 販売可能数
     */
    @Override
    public Map<UUID, Integer> findSellableQuantities(Collection<UUID> variantIds, UUID excludedHolderId, Instant now) {
        // 在庫ごとの有効な引当の合計（引当がなければ 0）
        var reserved = DSL.coalesce(
                DSL.select(DSL.sum(STOCK_RESERVATION.QUANTITY).cast(Integer.class))
                        .from(STOCK_RESERVATION)
                        .where(STOCK_RESERVATION.STOCK_ITEM_ID.eq(STOCK_ITEM.ID))
                        .and(ActiveReservations.condition(excludedHolderId, now))
                        .<Integer>asField(),
                0);
        var sellable = STOCK_ITEM.QUANTITY.minus(reserved);
        return dsl.select(STOCK_ITEM.VARIANT_ID, sellable)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.in(variantIds))
                .fetchMap(STOCK_ITEM.VARIANT_ID, sellable);
    }
}
