package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
     * 在庫数に増減量を加え、調整履歴を記録する（docs/design/modules/inventory.md）。<br />
     * 更新されなかったら、「負になる」「運営者の調整で上限を超える」「在庫がない」のどれかなので、増減量と今の在庫数でエラーを分ける。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 調整後の在庫数
     */
    @Override
    public int adjust(UUID variantId, StockAdjustment adjustment) {
        var delta = adjustment.delta();
        var limited = adjustment.operator() != null;

        // 差分の加算 ※調整後に負にならず、運営者の調整なら上限を超えない場合だけ更新する
        var condition = STOCK_ITEM.VARIANT_ID.eq(variantId).and(STOCK_ITEM.QUANTITY.plus(delta).ge(0));
        if (limited) {
            condition = condition.and(STOCK_ITEM.QUANTITY.plus(delta).le(StockAdjustment.MAX_MANUAL_QUANTITY));
        }
        var updated = dsl.update(STOCK_ITEM)
                .set(STOCK_ITEM.QUANTITY, STOCK_ITEM.QUANTITY.plus(delta))
                .set(STOCK_ITEM.UPDATED_AT, adjustment.adjustedAt())
                .where(condition)
                .returningResult(STOCK_ITEM.ID, STOCK_ITEM.QUANTITY)
                .fetchOptional()
                .orElseThrow(() -> failureOf(delta, limited, () -> dsl.select(STOCK_ITEM.QUANTITY)
                        .from(STOCK_ITEM)
                        .where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                        .fetchOptional(STOCK_ITEM.QUANTITY)));
        int quantityAfter = updated.value2();

        // 調整履歴の記録
        dsl.insertInto(STOCK_ADJUSTMENT)
                .set(STOCK_ADJUSTMENT.ID, adjustment.id())
                .set(STOCK_ADJUSTMENT.STOCK_ITEM_ID, updated.value1())
                .set(STOCK_ADJUSTMENT.DELTA, delta)
                .set(STOCK_ADJUSTMENT.REASON, adjustment.reason().name())
                .set(STOCK_ADJUSTMENT.MEMO, adjustment.memo())
                .set(STOCK_ADJUSTMENT.ORDER_ID, adjustment.orderId())
                .set(STOCK_ADJUSTMENT.ORDER_NUMBER, adjustment.orderNumber())
                .set(STOCK_ADJUSTMENT.QUANTITY_AFTER, quantityAfter)
                .set(STOCK_ADJUSTMENT.ADJUSTED_AT, adjustment.adjustedAt())
                .set(STOCK_ADJUSTMENT.ACTOR_ID, adjustment.operator() == null ? null : adjustment.operator().id())
                .set(STOCK_ADJUSTMENT.ACTOR_NAME, adjustment.operator() == null ? null : adjustment.operator().name())
                .execute();

        return quantityAfter;
    }

    /**
     * 調整が更新されなかったときのエラーを選ぶ（docs/design/modules/inventory.md）。<br />
     * 増やす調整は、上限を超えるときのほかは在庫があれば必ず更新されるので、上限を超えないなら更新の時点で在庫がなかったことになる。
     * 更新の後に作られた在庫は在庫数 0 なので、上限を超えない（運営者の 1 回の増減量は上限以下）。「負になる」「上限を超える」と誤らない。
     *
     * @param delta    増減量
     * @param limited  運営者の調整で、在庫数の上限を当てるか
     * @param quantity 今の在庫数。在庫がなければ空（増やす調整で上限を当てないときは読まない）
     * @return 業務のエラー
     */
    static BusinessException failureOf(int delta, boolean limited, Supplier<Optional<Integer>> quantity) {
        if (delta > 0) {
            if (!limited) {
                return BusinessException.of(CommonErrorCode.NOT_FOUND);
            }
            return quantity.get().filter(current -> current + delta > StockAdjustment.MAX_MANUAL_QUANTITY).isPresent()
                    ? BusinessException.of(InventoryErrorCode.STOCK_QUANTITY_EXCEEDED, List.of(StockAdjustment.MAX_MANUAL_QUANTITY))
                    : BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        return quantity.get().isPresent()
                ? BusinessException.of(InventoryErrorCode.INSUFFICIENT_STOCK)
                : BusinessException.of(CommonErrorCode.NOT_FOUND);
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
