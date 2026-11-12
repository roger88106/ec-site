package com.ecsite.module.inventory.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.application.StockQueryService;
import com.ecsite.module.inventory.application.StockReservationService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 販売可能数と引当の API
 *
 * <p>書き込みの操作は、呼んだ側のトランザクションに加わる。
 */
@Component
@RequiredArgsConstructor
public class StockReservationApi {

    /**
     * 引当のサービス
     */
    private final StockReservationService reservationService;

    /**
     * 在庫のクエリサービス
     */
    private final StockQueryService queryService;

    /**
     * 販売可能数（在庫数 − 有効な引当の合計）を返す。
     *
     * <p>在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（注文手続き中の自分の引当）。なければ null
     * @return バリアント ID → 販売可能数（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Integer> sellableQuantities(@NonNull Collection<UUID> variantIds, @Nullable UUID excludedHolderId) {
        return queryService.sellableQuantities(variantIds, excludedHolderId);
    }

    /**
     * 全品目を引き当てる。
     *
     * <p>同じ持ち主の引当は置き換える。
     *
     * @param holderId   持ち主 ID（注文手続き）
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空（足りなければ何も引き当てない）
     */
    public Set<UUID> reserve(@NonNull UUID holderId, @NonNull Map<UUID, Integer> quantities, @NonNull Instant expiresAt) {
        return reservationService.reserve(holderId, quantities, expiresAt);
    }

    /**
     * 持ち主の引当を解く。
     *
     * <p>なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    public void release(@NonNull UUID holderId) {
        reservationService.release(holderId);
    }

    /**
     * 引当を在庫数の減算に置き換え、理由「注文」で調整履歴を書く。
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
        return reservationService.commit(holderId, quantities, orderId, orderNumber);
    }

    /**
     * キャンセルした注文の数量を在庫数に戻し、理由「注文のキャンセル」で調整履歴を書く。
     *
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @throws BusinessException 数量が 0 のバリアントがある（{@code INVALID_STOCK_DELTA}）
     */
    public void restore(@NonNull Map<UUID, Integer> quantities, @NonNull UUID orderId, @NonNull String orderNumber) {
        reservationService.restore(quantities, orderId, orderNumber);
    }
}
