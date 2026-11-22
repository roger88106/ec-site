package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.id.IdGenerator;
import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import com.ecsite.module.inventory.domain.stock.vo.StockItemId;
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
     * 識別子の生成
     */
    private final IdGenerator idGenerator;

    /**
     * バリアントの在庫を、バリアント ID の順に行ロックして読み込む。
     *
     * @param variantIds バリアント ID
     * @return 在庫
     */
    @Override
    public List<LockedStock> lockStocks(Collection<VariantId> variantIds) {
        return dsl.select(STOCK_ITEM.ID, STOCK_ITEM.VARIANT_ID, STOCK_ITEM.QUANTITY)
                .from(STOCK_ITEM)
                .where(STOCK_ITEM.VARIANT_ID.in(variantIds.stream().map(VariantId::value).toList()))
                .orderBy(STOCK_ITEM.VARIANT_ID)
                .forUpdate()
                .fetch(record -> new LockedStock(new StockItemId(record.get(STOCK_ITEM.ID)),
                        new VariantId(record.get(STOCK_ITEM.VARIANT_ID)), record.get(STOCK_ITEM.QUANTITY)));
    }

    /**
     * 在庫ごとに、有効な引当の数量を合計する。
     *
     * @param stockItemIds     在庫 ID
     * @param excludedHolderId 数えない引当の持ち主
     * @param now              現在時刻
     * @return 在庫 ID → 引当の数量の合計。引当のない在庫は含まない
     */
    @Override
    public Map<StockItemId, Integer> sumActiveReservations(Collection<StockItemId> stockItemIds, ReservationHolderId excludedHolderId,
                                                          Instant now) {
        var total = DSL.sum(STOCK_RESERVATION.QUANTITY).cast(Integer.class);
        return dsl.select(STOCK_RESERVATION.STOCK_ITEM_ID, total)
                .from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.STOCK_ITEM_ID.in(stockItemIds.stream().map(StockItemId::value).toList()))
                .and(ActiveReservations.condition(excludedHolderId, now))
                .groupBy(STOCK_RESERVATION.STOCK_ITEM_ID)
                .fetchMap(record -> new StockItemId(record.get(STOCK_RESERVATION.STOCK_ITEM_ID)), record -> record.get(total));
    }

    /**
     * 持ち主の引当を加える。持ち主の前の引当は消さない。
     *
     * @param holderId              持ち主 ID
     * @param quantitiesByStockItem 在庫 ID → 数量
     * @param expiresAt             期限
     * @param now                   現在時刻
     */
    @Override
    public void insert(ReservationHolderId holderId, Map<StockItemId, Integer> quantitiesByStockItem, Instant expiresAt, Instant now) {
        var insert = dsl.insertInto(STOCK_RESERVATION,
                STOCK_RESERVATION.ID, STOCK_RESERVATION.HOLDER_ID, STOCK_RESERVATION.STOCK_ITEM_ID,
                STOCK_RESERVATION.QUANTITY, STOCK_RESERVATION.EXPIRES_AT, STOCK_RESERVATION.CREATED_AT);
        for (var entry : quantitiesByStockItem.entrySet()) {
            insert = insert.values(idGenerator.generate(), holderId.value(), entry.getKey().value(), entry.getValue(), expiresAt, now);
        }
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
    public void deleteByHolder(ReservationHolderId holderId) {
        dsl.deleteFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.HOLDER_ID.eq(holderId.value())).execute();
    }

    /**
     * 複数の持ち主の引当を、1 回の削除で消す。空のコレクションでは SQL を実行しない。
     *
     * @param holderIds 持ち主 ID
     */
    @Override
    public void deleteByHolders(Collection<ReservationHolderId> holderIds) {
        if (holderIds.isEmpty()) {
            return;
        }
        var ids = holderIds.stream().map(ReservationHolderId::value).toList();
        dsl.deleteFrom(STOCK_RESERVATION).where(STOCK_RESERVATION.HOLDER_ID.in(ids)).execute();
    }

    /**
     * 期限が指定した時刻より前の引当を、件数の上限まで消す。
     *
     * @param expiredBefore この時刻
     * @param limit         件数の上限
     * @return 消した件数
     */
    @Override
    public int deleteExpiredBefore(Instant expiredBefore, int limit) {
        // 選ぶのと消すのを 1 文にし、他のトランザクションがロックしている行は飛ばす（次の回で消す）
        // ※選ぶ行は MATERIALIZED の CTE で 1 回だけ確定させる。IN の副問い合わせに直接書くと、実行計画によっては
        //   外側の行ごとに再評価され、前の評価で消した行を飛ばして次の行を選ぶので、件数の上限より多く消しうる
        var targets = DSL.name("targets").fields("id").asMaterialized(DSL.select(STOCK_RESERVATION.ID)
                .from(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.EXPIRES_AT.lt(expiredBefore))
                .limit(limit)
                .forUpdate()
                .skipLocked());
        return dsl.with(targets)
                .delete(STOCK_RESERVATION)
                .where(STOCK_RESERVATION.ID.in(DSL.select(targets.field("id", UUID.class)).from(targets)))
                .execute();
    }
}
