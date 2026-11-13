package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.RowN;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 調整の一覧（VALUES）の列の名前。調整履歴の列と同じ名前にして、履歴の列で参照する
     */
    private static final String[] CHANGE_COLUMNS = {
            STOCK_ITEM.VARIANT_ID.getName(), STOCK_ADJUSTMENT.ID.getName(), STOCK_ADJUSTMENT.DELTA.getName(), "limited",
            STOCK_ADJUSTMENT.REASON.getName(), STOCK_ADJUSTMENT.MEMO.getName(), STOCK_ADJUSTMENT.ORDER_ID.getName(),
            STOCK_ADJUSTMENT.ORDER_NUMBER.getName(), STOCK_ADJUSTMENT.ADJUSTED_AT.getName(), STOCK_ADJUSTMENT.ACTOR_ID.getName(),
            STOCK_ADJUSTMENT.ACTOR_NAME.getName()};

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 時計（登録・更新日時）
     */
    private final Clock clock;

    /**
     * 在庫数 0 の在庫を作る。
     *
     * <p>バリアントの一意制約で、すでにあれば何もしない。
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
     * 在庫数に増減量を加え、調整履歴を記録する。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 調整後の在庫数
     * @throws BusinessException 調整後に負になる（{@code INSUFFICIENT_STOCK}）
     * @throws BusinessException 運営者の調整で在庫数が上限を超える（{@code STOCK_QUANTITY_EXCEEDED}）
     * @throws BusinessException 在庫がない（{@code NOT_FOUND}）
     */
    @Override
    public int adjust(UUID variantId, StockAdjustment adjustment) {
        return Objects.requireNonNull(adjustAll(Map.of(variantId, adjustment)).get(variantId), "調整したバリアントは調整後の在庫数を持つ");
    }

    /**
     * 複数のバリアントの在庫数に増減量を加え、調整履歴を記録する。
     *
     * <p>在庫数の更新（{@code UPDATE ... RETURNING}）を共通テーブル式にし、その結果から履歴を記録する 1 つの文にする。
     * 更新しなかった在庫の履歴は記録しない。
     *
     * @param adjustments バリアント ID → 在庫調整
     * @return バリアント ID → 調整後の在庫数
     * @throws BusinessException 調整後に負になる（{@code INSUFFICIENT_STOCK}）
     * @throws BusinessException 運営者の調整で在庫数が上限を超える（{@code STOCK_QUANTITY_EXCEEDED}）
     * @throws BusinessException 在庫がない（{@code NOT_FOUND}）
     */
    @Override
    public Map<UUID, Integer> adjustAll(Map<UUID, StockAdjustment> adjustments) {
        if (adjustments.isEmpty()) {
            return Map.of();
        }

        // 調整の一覧
        var changes = DSL.values(adjustments.entrySet().stream()
                        .map(entry -> changeRow(entry.getKey(), entry.getValue()))
                        .toArray(RowN[]::new))
                .as("changes", CHANGE_COLUMNS);
        var delta = changes.field(STOCK_ADJUSTMENT.DELTA);
        var limited = DSL.field(DSL.name("changes", "limited"), Boolean.class);

        // 差分の加算 ※調整後に負にならず、運営者の調整なら上限を超えない在庫だけを更新する
        var updated = DSL.name("updated").as(dsl.update(STOCK_ITEM)
                .set(STOCK_ITEM.QUANTITY, STOCK_ITEM.QUANTITY.plus(delta))
                .set(STOCK_ITEM.UPDATED_AT, changes.field(STOCK_ADJUSTMENT.ADJUSTED_AT))
                .from(changes)
                .where(STOCK_ITEM.VARIANT_ID.eq(changes.field(STOCK_ITEM.VARIANT_ID)))
                .and(STOCK_ITEM.QUANTITY.plus(delta).ge(0))
                .and(limited.isFalse().or(STOCK_ITEM.QUANTITY.plus(delta).le(StockAdjustment.MAX_MANUAL_QUANTITY)))
                .returningResult(
                        STOCK_ITEM.ID.as(STOCK_ADJUSTMENT.STOCK_ITEM_ID.getName()),
                        STOCK_ITEM.QUANTITY.as(STOCK_ADJUSTMENT.QUANTITY_AFTER.getName()),
                        changes.field(STOCK_ADJUSTMENT.ID), delta, changes.field(STOCK_ADJUSTMENT.REASON),
                        changes.field(STOCK_ADJUSTMENT.MEMO), changes.field(STOCK_ADJUSTMENT.ORDER_ID),
                        changes.field(STOCK_ADJUSTMENT.ORDER_NUMBER), changes.field(STOCK_ADJUSTMENT.ADJUSTED_AT),
                        changes.field(STOCK_ADJUSTMENT.ACTOR_ID), changes.field(STOCK_ADJUSTMENT.ACTOR_NAME)));

        // 調整履歴の記録 ※更新した在庫の分だけ
        var quantityAfterById = dsl.with(updated)
                .insertInto(STOCK_ADJUSTMENT, STOCK_ADJUSTMENT.ID, STOCK_ADJUSTMENT.STOCK_ITEM_ID, STOCK_ADJUSTMENT.DELTA,
                        STOCK_ADJUSTMENT.REASON, STOCK_ADJUSTMENT.MEMO, STOCK_ADJUSTMENT.ORDER_ID, STOCK_ADJUSTMENT.ORDER_NUMBER,
                        STOCK_ADJUSTMENT.QUANTITY_AFTER, STOCK_ADJUSTMENT.ADJUSTED_AT, STOCK_ADJUSTMENT.ACTOR_ID,
                        STOCK_ADJUSTMENT.ACTOR_NAME)
                .select(DSL.select(updated.field(STOCK_ADJUSTMENT.ID), updated.field(STOCK_ADJUSTMENT.STOCK_ITEM_ID),
                                updated.field(STOCK_ADJUSTMENT.DELTA), updated.field(STOCK_ADJUSTMENT.REASON),
                                updated.field(STOCK_ADJUSTMENT.MEMO), updated.field(STOCK_ADJUSTMENT.ORDER_ID),
                                updated.field(STOCK_ADJUSTMENT.ORDER_NUMBER), updated.field(STOCK_ADJUSTMENT.QUANTITY_AFTER),
                                updated.field(STOCK_ADJUSTMENT.ADJUSTED_AT), updated.field(STOCK_ADJUSTMENT.ACTOR_ID),
                                updated.field(STOCK_ADJUSTMENT.ACTOR_NAME))
                        .from(updated))
                .returningResult(STOCK_ADJUSTMENT.ID, STOCK_ADJUSTMENT.QUANTITY_AFTER)
                .fetchMap(STOCK_ADJUSTMENT.ID, STOCK_ADJUSTMENT.QUANTITY_AFTER);

        // 更新されなかった在庫があれば、増減量と今の在庫数で例外を分ける
        var quantityAfterByVariant = new HashMap<UUID, Integer>();
        adjustments.forEach((variantId, adjustment) -> {
            var quantityAfter = quantityAfterById.get(adjustment.id());
            if (quantityAfter == null) {
                throw failureOf(adjustment.delta(), adjustment.operator() != null, () -> currentQuantity(variantId));
            }
            quantityAfterByVariant.put(variantId, quantityAfter);
        });
        return quantityAfterByVariant;
    }

    /**
     * バリアントの在庫を消す。
     *
     * <p>調整履歴は外部キーの ON DELETE CASCADE で消える。
     *
     * @param variantId バリアント ID
     */
    @Override
    public void deleteByVariantId(UUID variantId) {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.VARIANT_ID.eq(variantId)).execute();
    }

    /**
     * 商品のすべての在庫を消す。
     *
     * <p>調整履歴は外部キーの ON DELETE CASCADE で消える。
     *
     * @param productId 商品 ID
     */
    @Override
    public void deleteByProductId(UUID productId) {
        dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(productId)).execute();
    }

// ============================== テストのために package-private にしたメソッド ==============================

    /**
     * 調整が更新されなかったときの例外を選ぶ。
     *
     * @param delta    増減量
     * @param limited  運営者の調整で、在庫数の上限を当てるか
     * @param quantity 今の在庫数。在庫がなければ空（増やす調整で上限を当てないときは読まない）
     * @return 例外（{@code STOCK_QUANTITY_EXCEEDED}、{@code INSUFFICIENT_STOCK}、{@code NOT_FOUND} のどれか）
     */
    static BusinessException failureOf(int delta, boolean limited, Supplier<Optional<Integer>> quantity) {
        // 増やす調整は、上限を超えるときのほかは在庫があれば必ず更新されるので、上限を超えないなら更新の時点で在庫がなかった
        if (delta > 0) {
            if (!limited) {
                return BusinessException.of(CommonErrorCode.NOT_FOUND);
            }
            // 更新の後に作られた在庫は在庫数 0 で、運営者の 1 回の増減量は上限以下なので、上限を超えたと誤らない
            return quantity.get().filter(current -> current + delta > StockAdjustment.MAX_MANUAL_QUANTITY).isPresent()
                    ? BusinessException.of(InventoryErrorCode.STOCK_QUANTITY_EXCEEDED, List.of(StockAdjustment.MAX_MANUAL_QUANTITY))
                    : BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        // 減らす調整は、在庫があれば負になるときだけ更新されない
        return quantity.get().isPresent()
                ? BusinessException.of(InventoryErrorCode.INSUFFICIENT_STOCK)
                : BusinessException.of(CommonErrorCode.NOT_FOUND);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 調整の一覧の 1 行を作る。
     *
     * <p>値に列の型を付けて、NULL の値も DB に同じ型で読ませる。
     *
     * @param variantId  バリアント ID
     * @param adjustment 在庫調整
     * @return 列の名前は {@link #CHANGE_COLUMNS} の順
     */
    private static RowN changeRow(UUID variantId, StockAdjustment adjustment) {
        var operator = adjustment.operator();
        return DSL.row(new SelectField<?>[]{
                DSL.val(variantId, STOCK_ITEM.VARIANT_ID),
                DSL.val(adjustment.id(), STOCK_ADJUSTMENT.ID),
                DSL.val(adjustment.delta(), STOCK_ADJUSTMENT.DELTA),
                DSL.val(operator != null, SQLDataType.BOOLEAN),
                DSL.val(adjustment.reason().name(), STOCK_ADJUSTMENT.REASON),
                DSL.val(adjustment.memo(), STOCK_ADJUSTMENT.MEMO),
                DSL.val(adjustment.orderId(), STOCK_ADJUSTMENT.ORDER_ID),
                DSL.val(adjustment.orderNumber(), STOCK_ADJUSTMENT.ORDER_NUMBER),
                DSL.val(adjustment.adjustedAt(), STOCK_ADJUSTMENT.ADJUSTED_AT),
                DSL.val(operator == null ? null : operator.id(), STOCK_ADJUSTMENT.ACTOR_ID),
                DSL.val(operator == null ? null : operator.name(), STOCK_ADJUSTMENT.ACTOR_NAME)});
    }

    /**
     * バリアントの今の在庫数を読む。
     *
     * @param variantId バリアント ID
     * @return 在庫数。在庫がなければ空
     */
    private Optional<Integer> currentQuantity(UUID variantId) {
        return dsl.select(STOCK_ITEM.QUANTITY)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.eq(variantId))
                .fetchOptional(STOCK_ITEM.QUANTITY);
    }
}
