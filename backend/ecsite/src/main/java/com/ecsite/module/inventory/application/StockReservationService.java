package com.ecsite.module.inventory.application;

import com.ecsite.module.inventory.application.port.StockQueryRepository;
import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.StockAllocation;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 引当のサービス（docs/design/modules/inventory.md「引当」）。<br />
 * 呼び出し元（注文モジュール）のトランザクションに参加し、注文の成立などと同じトランザクションで在庫を扱う。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationService {

    /**
     * 引当リポジトリ
     */
    private final StockReservationRepository reservationRepository;

    /**
     * 在庫リポジトリ（在庫数の増減と履歴）
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 在庫クエリリポジトリ（販売可能数の照会）
     */
    private final StockQueryRepository stockQueryRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限が指定した時刻より前の引当を、件数の上限まで消す（片付けのジョブから呼ぶ）。
     *
     * @param expiredBefore この時刻より前に期限が切れた引当を消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    @Transactional
    public int deleteExpired(Instant expiredBefore, int limit) {
        return reservationRepository.deleteExpiredBefore(expiredBefore, limit);
    }

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
     * 全品目を引き当てる。1 つでも足りなければ、何も書かずに足りない品目を返す。<br />
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
     * 持ち主の引当を解く。なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    @Transactional
    public void release(UUID holderId) {
        reservationRepository.deleteByHolder(holderId);
    }

    /**
     * 引当を在庫数の減算に置き換える。<br />
     * 引当の期限が切れていても、その時点で足りれば減算する（引き当て直しを兼ねる）。1 つでも足りなければ何も変えない。
     *
     * @param holderId    持ち主 ID（注文手続き）
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @return 足りないバリアントの ID。減算したら空
     */
    @Transactional
    public Set<UUID> commit(UUID holderId, Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        var now = clock.instant();
        var locked = lockStocks(quantities.keySet());
        var shortages = StockAllocation.shortages(quantities, sellableOf(locked, holderId, now));
        if (!shortages.isEmpty()) {
            return shortages;
        }
        // 行ロックの中で足りることを確認したので、減算は必ず成功する
        locked.keySet().forEach(variantId -> stockItemRepository.adjust(variantId, StockAdjustment.forOrder(
                -quantities.get(variantId), StockAdjustmentReason.ORDER, orderId, orderNumber, now)));
        reservationRepository.deleteByHolder(holderId);
        return shortages;
    }

    /**
     * キャンセルした注文の数量を在庫数に戻す。<br />
     * 在庫がないバリアント（削除済み）は戻せないので、警告を残して飛ばす。C 側に表示された商品のバリアントは削除できないため、通常は起きない。
     *
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     */
    @Transactional
    public void restore(Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        var now = clock.instant();
        var locked = lockStocks(quantities.keySet());
        quantities.forEach((variantId, quantity) -> {
            if (!locked.containsKey(variantId)) {
                log.warn("在庫がないため、注文のキャンセルの数量を戻せません: orderId={}, variantId={}", orderId, variantId);
                return;
            }
            stockItemRepository.adjust(variantId, StockAdjustment.forOrder(
                    quantity, StockAdjustmentReason.ORDER_CANCELLATION, orderId, orderNumber, now));
        });
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
