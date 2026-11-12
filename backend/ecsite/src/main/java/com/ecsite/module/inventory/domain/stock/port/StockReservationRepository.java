package com.ecsite.module.inventory.domain.stock.port;

import com.ecsite.module.inventory.domain.stock.LockedStock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 引当のリポジトリ
 */
public interface StockReservationRepository {

    /**
     * バリアントの在庫を、バリアント ID の順に行ロックして読み込む。
     *
     * <p>順序を固定して、複数の在庫をロックするときのデッドロックを避ける。ロックは、呼んだ側のトランザクションの終わりまで続く。
     *
     * @param variantIds バリアント ID
     * @return 在庫（バリアント ID の順）。在庫のないバリアントは含まない
     */
    List<LockedStock> lockStocks(Collection<UUID> variantIds);

    /**
     * 在庫ごとに、有効な（期限の切れていない）引当の数量を合計する。
     *
     * @param stockItemIds     在庫 ID
     * @param excludedHolderId 数えない引当の持ち主
     * @param now              現在時刻
     * @return 在庫 ID → 引当の数量の合計。引当のない在庫は含まない
     */
    Map<UUID, Integer> sumActiveReservations(Collection<UUID> stockItemIds, UUID excludedHolderId, Instant now);

    /**
     * 持ち主の引当を、指定した内容に置き換える。
     *
     * @param holderId               持ち主 ID
     * @param quantitiesByStockItem  在庫 ID → 数量
     * @param expiresAt              期限
     * @param now                    現在時刻
     */
    void replace(UUID holderId, Map<UUID, Integer> quantitiesByStockItem, Instant expiresAt, Instant now);

    /**
     * 持ち主の引当を消す。なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    void deleteByHolder(UUID holderId);

    /**
     * 期限が指定した時刻より前の引当を、件数の上限まで消す。
     *
     * @param expiredBefore この時刻より前に期限が切れた引当を消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    int deleteExpiredBefore(Instant expiredBefore, int limit);
}
