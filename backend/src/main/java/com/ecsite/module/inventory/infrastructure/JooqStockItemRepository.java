package com.ecsite.module.inventory.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.stock.InventoryErrorCode;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;

/**
 * 在庫のリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqStockItemRepository implements StockItemRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 時計（登録・更新日時）
     */
    private final Clock clock;

    /**
     * 在庫数 0 の在庫を作る。バリアントの一意制約で、すでにあれば何もしない。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    @Override
    public void createIfAbsent(UUID variantId, UUID productId) {
        var now = clock.instant();
        dsl.insertInto(STOCK_ITEM)
                .set(STOCK_ITEM.ID, UUID.randomUUID())
                .set(STOCK_ITEM.VARIANT_ID, variantId)
                .set(STOCK_ITEM.PRODUCT_ID, productId)
                .set(STOCK_ITEM.QUANTITY, 0)
                .set(STOCK_ITEM.CREATED_AT, now)
                .set(STOCK_ITEM.UPDATED_AT, now)
                .onConflict(STOCK_ITEM.VARIANT_ID)
                .doNothing()
                .execute();
    }

    /**
     * 在庫数に増減量を足し、調整履歴を記録する（docs/modules/inventory.md）。<br />
     * 更新されなかったら、「負になる」か「在庫がない」のどちらかなので、在庫の有無でエラーを分ける。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 調整後の在庫数
     */
    @Override
    public int adjust(UUID variantId, StockAdjustment adjustment) {
        var delta = adjustment.delta();

        // 差分の加算 ※調整後に負にならない場合だけ更新する
        var updated = dsl.update(STOCK_ITEM)
                .set(STOCK_ITEM.QUANTITY, STOCK_ITEM.QUANTITY.plus(delta))
                .set(STOCK_ITEM.UPDATED_AT, adjustment.adjustedAt())
                .where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                .and(STOCK_ITEM.QUANTITY.plus(delta).ge(0))
                .returningResult(STOCK_ITEM.ID, STOCK_ITEM.QUANTITY)
                .fetchOptional()
                .orElseThrow(() -> dsl.fetchExists(STOCK_ITEM, STOCK_ITEM.VARIANT_ID.eq(variantId))
                        ? BusinessException.of(InventoryErrorCode.INSUFFICIENT_STOCK)
                        : BusinessException.of(CommonErrorCode.NOT_FOUND));
        int quantityAfter = updated.value2();

        // 調整履歴の記録
        dsl.insertInto(STOCK_ADJUSTMENT)
                .set(STOCK_ADJUSTMENT.ID, adjustment.id())
                .set(STOCK_ADJUSTMENT.STOCK_ITEM_ID, updated.value1())
                .set(STOCK_ADJUSTMENT.DELTA, delta)
                .set(STOCK_ADJUSTMENT.REASON, adjustment.reason().name())
                .set(STOCK_ADJUSTMENT.MEMO, adjustment.memo())
                .set(STOCK_ADJUSTMENT.QUANTITY_AFTER, quantityAfter)
                .set(STOCK_ADJUSTMENT.ADJUSTED_AT, adjustment.adjustedAt())
                .execute();

        return quantityAfter;
    }

    /**
     * バリアントの在庫を消す。調整履歴は外部キーの ON DELETE CASCADE で消える。
     *
     * @param variantId バリアント ID
     */
    @Override
    public void deleteByVariantId(UUID variantId) {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId)).execute();
    }

    /**
     * 商品のすべての在庫を消す。調整履歴は外部キーの ON DELETE CASCADE で消える。
     *
     * @param productId 商品 ID
     */
    @Override
    public void deleteByProductId(UUID productId) {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(productId)).execute();
    }
}
