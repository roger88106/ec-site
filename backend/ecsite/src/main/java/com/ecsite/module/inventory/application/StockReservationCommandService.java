package com.ecsite.module.inventory.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.domain.stock.LockedStock;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.StockAllocation;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.inventory.domain.stock.port.StockReservationRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引当のコマンドサービス
 *
 * <p>呼んだ側のトランザクションがあれば、それに加わる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockReservationCommandService {

    /**
     * 引当リポジトリ
     */
    private final StockReservationRepository reservationRepository;

    /**
     * 在庫リポジトリ（在庫数の増減と履歴）
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 期限が指定した時刻より前の引当を、件数の上限まで消す。
     *
     * @param expiredBefore この時刻より前に期限が切れた引当を消す
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    public int deleteExpired(@NonNull Instant expiredBefore, int limit) {
        return reservationRepository.deleteExpiredBefore(expiredBefore, limit);
    }

    /**
     * 全品目を引き当てる。
     *
     * <p>1 つでも足りなければ、何も書かずに足りない品目を返す。同じ持ち主の引当は置き換える。判定では自分の引当を数えない。
     *
     * @param holderId   持ち主 ID（注文手続き）
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空
     */
    public Set<UUID> reserve(@NonNull UUID holderId, @NonNull Map<UUID, Integer> quantities, @NonNull Instant expiresAt) {
        var now = clock.instant();

        // 在庫の行ロック ※同じ在庫への引当を 1 つずつ判定するため、判定の前にロックする
        var locked = lockStocks(quantities.keySet());

        // 足りるかの判定
        var shortages = StockAllocation.shortages(quantities, sellableOf(locked, holderId, now));

        // 引当の置き換え ※全て足りるときだけ
        if (shortages.isEmpty()) {
            var byStockItem = locked.values().stream()
                    .collect(Collectors.toMap(LockedStock::stockItemId, stock -> quantities.get(stock.variantId())));
            reservationRepository.replace(holderId, byStockItem, expiresAt, now);
        }
        return shortages;
    }

    /**
     * 持ち主の引当を解く。
     *
     * <p>なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    public void release(@NonNull UUID holderId) {
        reservationRepository.deleteByHolder(holderId);
    }

    /**
     * 引当を在庫数の減算に置き換える。
     *
     * <p>引当の期限が切れていても、その時点で足りれば減算する。
     * 1 つでも足りなければ在庫数を変えずに足りない品目を返すが、持ち主の引当は解いたままになる。
     *
     * @param holderId    持ち主 ID（注文手続き）
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @return 足りないバリアントの ID。減算したら空
     * @throws BusinessException 数量が 0 のバリアントがある（{@code INVALID_STOCK_DELTA}）
     */
    public Set<UUID> commit(@NonNull UUID holderId, @NonNull Map<UUID, Integer> quantities, @NonNull UUID orderId,
                            @NonNull String orderNumber) {
        var now = clock.instant();

        // 引当の解除 ※注文手続きの開始と同じく、在庫のロックより先に行い、デッドロックを防ぐ。
        // 販売可能数は持ち主の引当を数えないので、先に解いても判定は変わらない
        reservationRepository.deleteByHolder(holderId);

        // 在庫の行ロック ※同じ在庫への減算を 1 つずつ判定するため、判定の前にロックする
        var locked = lockStocks(quantities.keySet());

        // 足りるかの判定
        var shortages = StockAllocation.shortages(quantities, sellableOf(locked, holderId, now));
        if (!shortages.isEmpty()) {
            return shortages;
        }

        // 在庫数の減算 ※行ロックの中で足りることを確認したので、必ず成功する
        var adjustments = new HashMap<UUID, StockAdjustment>();
        for (var variantId : locked.keySet()) {
            var quantity = Objects.requireNonNull(quantities.get(variantId), "ロックした在庫は数量を指定したバリアントのもの");
            adjustments.put(variantId, StockAdjustment.forOrder(-quantity, StockAdjustmentReason.ORDER, orderId, orderNumber, now));
        }
        stockItemRepository.adjustAll(adjustments);
        return Set.of();
    }

    /**
     * キャンセルした注文の数量を在庫数に戻す。
     *
     * <p>在庫がないバリアントは、警告のログを残して飛ばす。
     *
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @throws BusinessException 数量が 0 のバリアントがある（{@code INVALID_STOCK_DELTA}）
     */
    public void restore(@NonNull Map<UUID, Integer> quantities, @NonNull UUID orderId, @NonNull String orderNumber) {
        var now = clock.instant();

        // 在庫の行ロック
        var locked = lockStocks(quantities.keySet());

        // 在庫数の戻し
        var adjustments = new HashMap<UUID, StockAdjustment>();
        for (var entry : quantities.entrySet()) {
            var variantId = entry.getKey();
            // 削除済みのバリアントは戻せない（C 側に表示された商品のバリアントは削除できないので、通常は起きない）
            if (!locked.containsKey(variantId)) {
                log.warn("在庫がないため、注文のキャンセルの数量を戻せません: orderId={}, variantId={}", orderId, variantId);
                continue;
            }
            adjustments.put(variantId, StockAdjustment.forOrder(
                    entry.getValue(), StockAdjustmentReason.ORDER_CANCELLATION, orderId, orderNumber, now));
        }
        stockItemRepository.adjustAll(adjustments);
    }

// ============================== プライベートメソッド ==============================

    /**
     * バリアントの在庫を行ロック（{@code SELECT ... FOR UPDATE}）して読み込む。
     *
     * <p>ロックはトランザクションの終わりまで続き、同じ在庫の引当と減算を 1 つずつにする。
     *
     * @return バリアント ID → 在庫。在庫のないバリアントは含まない
     */
    private Map<UUID, LockedStock> lockStocks(Collection<UUID> variantIds) {
        List<LockedStock> stocks = reservationRepository.lockStocks(variantIds);
        return stocks.stream().collect(Collectors.toMap(LockedStock::variantId, Function.identity()));
    }

    /**
     * ロックした在庫の販売可能数を求める。
     *
     * <p>持ち主自身の引当は数えない。
     *
     * @return バリアント ID → 販売可能数
     */
    private Map<UUID, Integer> sellableOf(Map<UUID, LockedStock> locked, UUID holderId, Instant now) {
        var stockItemIds = locked.values().stream().map(LockedStock::stockItemId).toList();
        // 在庫がなければ、引当を検索しない
        var reserved = stockItemIds.isEmpty()
                ? Map.<UUID, Integer>of()
                : reservationRepository.sumActiveReservations(stockItemIds, holderId, now);
        var sellable = new HashMap<UUID, Integer>();
        locked.values().forEach(stock ->
                sellable.put(stock.variantId(), stock.quantity() - reserved.getOrDefault(stock.stockItemId(), 0)));
        return sellable;
    }
}
