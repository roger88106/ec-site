package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;

/**
 * 引当のリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqStockReservationRepository implements StockReservationRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * バリアントの在庫を、バリアント ID の順に行ロックして読み込む。
     *
     * @param variantIds バリアント ID
     * @return 在庫
     */
    @Override
    public List<LockedStock> lockStocks(Collection<UUID> variantIds) {
        return dsl.select(STOCK_ITEM.ID, STOCK_ITEM.VARIANT_ID, STOCK_ITEM.QUANTITY)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.in(variantIds))
                .orderBy(STOCK_ITEM.VARIANT_ID)
                .forUpdate()
                .fetch(record -> new LockedStock(record.value1(), record.value2(), record.value3()));
    }

    /**
     * 在庫ごとに、有効な引当の数量を合計する。
     *
     * @param stockItemIds     在庫 ID
     * @param excludedHolderId 除く持ち主
     * @param now              現在時刻
     * @return 在庫 ID → 引当の数量の合計
     */
    @Override
    public Map<UUID, Integer> sumActiveReservations(Collection<UUID> stockItemIds, UUID excludedHolderId, Instant now) {
        var total = DSL.sum(STOCK_RESERVATION.QUANTITY).cast(Integer.class);
        return dsl.select(STOCK_RESERVATION.STOCK_ITEM_ID, total)
                .from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.STOCK_ITEM_ID.in(stockItemIds))
                .and(ActiveReservations.condition(excludedHolderId, now))
                .groupBy(STOCK_RESERVATION.STOCK_ITEM_ID)
                .fetchMap(STOCK_RESERVATION.STOCK_ITEM_ID, total);
    }

    /**
     * 持ち主の引当を消してから、指定した内容で再作成する。
     *
     * @param holderId              持ち主 ID
     * @param quantitiesByStockItem 在庫 ID → 数量
     * @param expiresAt             期限
     * @param now                   現在時刻
     */
    @Override
    public void replace(UUID holderId, Map<UUID, Integer> quantitiesByStockItem, Instant expiresAt, Instant now) {
        deleteByHolder(holderId);
        var insert = dsl.insertInto(STOCK_RESERVATION,
                STOCK_RESERVATION.ID, STOCK_RESERVATION.HOLDER_ID, STOCK_RESERVATION.STOCK_ITEM_ID,
                STOCK_RESERVATION.QUANTITY, STOCK_RESERVATION.EXPIRES_AT, STOCK_RESERVATION.CREATED_AT);
        quantitiesByStockItem.forEach((stockItemId, quantity) ->
                insert.values(UUID.randomUUID(), holderId, stockItemId, quantity, expiresAt, now));
        if (!quantitiesByStockItem.isEmpty()) {
            insert.execute();
        }
    }

    /**
     * 持ち主の引当を消す。
     *
     * @param holderId 持ち主 ID
     */
    @Override
    public void deleteByHolder(UUID holderId) {
        dsl.deleteFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.HOLDER_ID.eq(holderId)).execute();
    }

    /**
     * 期限が指定した時刻より前の引当を、件数の上限まで消す。<br />
     * 選ぶのと消すのを 1 文にする。他のトランザクションがロックしている行は飛ばす（次の回で消す）。
     *
     * @param expiredBefore この時刻
     * @param limit         件数の上限
     * @return 消した件数
     */
    @Override
    public int deleteExpiredBefore(Instant expiredBefore, int limit) {
        return dsl.deleteFrom(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.ID.in(dsl.select(STOCK_RESERVATION.ID)
                        .from(STOCK_RESERVATION)
                        .where(STOCK_RESERVATION.EXPIRES_AT.lt(expiredBefore))
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()))
                .execute();
    }
}
