package com.ecsite.module.inventory.application;

import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.StockAllocation;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引当のサービス（docs/modules/inventory.md「引当」、spec 002 BR-11）。<br />
 * 呼び出し元（注文モジュール）のトランザクションに参加し、注文の成立などと同じトランザクションで在庫を扱う（plan 002 第 2 節）。
 */
@Service
@RequiredArgsConstructor
public class StockReservationService {

    /**
     * 引当リポジトリ
     */
    private final StockReservationRepository reservationRepository;

    /**
     * 在庫クエリリポジトリ（販売可能数の照会）
     */
    private final StockQueryRepository stockQueryRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 販売可能数を返す。在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（注文手続き中の自分の引当）。なければ null
     * @return バリアント ID → 販売可能数（指定したすべてのバリアントを含む）
     */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, UUID excludedHolderId) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        var found = stockQueryRepository.findSellableQuantities(variantIds, excludedHolderId, clock.instant());
        var sellable = new LinkedHashMap<UUID, Integer>();
        variantIds.forEach(variantId -> sellable.put(variantId, found.getOrDefault(variantId, 0)));
        return sellable;
    }

    /**
     * 全品目を引き当てる（AC-82、83）。1 つでも足りなければ、何も書かずに足りない品目を返す。<br />
     * 同じ持ち主の引当は置き換える。判定では自分の引当を数えない。
     *
     * @param holderId   持ち主 ID（注文手続き）
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空
     */
    @Transactional
    public Set<UUID> reserve(UUID holderId, Map<UUID, Integer> quantities, Instant expiresAt) {
        var now = clock.instant();
        var locked = lockStocks(quantities.keySet());
        var shortages = StockAllocation.shortages(quantities, sellableOf(locked, holderId, now));
        if (shortages.isEmpty()) {
            var byStockItem = locked.values().stream()
                    .collect(Collectors.toMap(LockedStock::stockItemId, stock -> quantities.get(stock.variantId())));
            reservationRepository.replace(holderId, byStockItem, expiresAt, now);
        }
        return shortages;
    }

    /**
     * 持ち主の引当を解く（AC-84）。なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    @Transactional
    public void release(UUID holderId) {
        reservationRepository.deleteByHolder(holderId);
    }

// ============================== プライベートメソッド ==============================

    /**
     * バリアントの在庫を行ロックして読み込む。
     *
     * @return バリアント ID → 在庫。在庫のないバリアントは含まない
     */
    private Map<UUID, LockedStock> lockStocks(Collection<UUID> variantIds) {
        List<LockedStock> stocks = reservationRepository.lockStocks(variantIds);
        return stocks.stream().collect(Collectors.toMap(LockedStock::variantId, Function.identity()));
    }

    /**
     * ロックした在庫の販売可能数を求める。持ち主自身の引当は数えない。
     *
     * @return バリアント ID → 販売可能数
     */
    private Map<UUID, Integer> sellableOf(Map<UUID, LockedStock> locked, UUID holderId, Instant now) {
        var stockItemIds = locked.values().stream().map(LockedStock::stockItemId).toList();
        var reserved = stockItemIds.isEmpty()
                ? Map.<UUID, Integer>of()
                : reservationRepository.sumActiveReservations(stockItemIds, holderId, now);
        var sellable = new HashMap<UUID, Integer>();
        locked.values().forEach(stock ->
                sellable.put(stock.variantId(), stock.quantity() - reserved.getOrDefault(stock.stockItemId(), 0)));
        return sellable;
    }
}
