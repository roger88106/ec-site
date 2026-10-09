package com.ecsite.module.inventory.infrastructure;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.UUID;

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
