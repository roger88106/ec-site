package com.ecsite.module.inventory.api;

import com.ecsite.module.inventory.application.StockQueryService;
import com.ecsite.module.inventory.application.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * カートと注文に公開する、販売可能数と引当の API（docs/03-機能仕様/inventory.md「引当」）。<br />
 * 書き込みの操作は呼び出し元のトランザクションに参加する。
 */
@Component
@RequiredArgsConstructor
public class StockReservationApi {

    /**
     * 引当のサービス
     */
    private final StockReservationService reservationService;

    /**
     * 参照のサービス（販売可能数）
     */
    private final StockQueryService queryService;

    /**
     * 販売可能数（在庫数 − 有効な引当の合計）を返す。在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（注文手続き中の自分の引当）。なければ null
     * @return バリアント ID → 販売可能数（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, @Nullable UUID excludedHolderId) {
        return queryService.sellableQuantities(variantIds, excludedHolderId);
    }

    /**
     * 全品目を引き当てる。同じ持ち主の引当は置き換える。
     *
     * @param holderId   持ち主 ID（注文手続き）
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空（足りなければ何も引き当てない）
     */
    public Set<UUID> reserve(UUID holderId, Map<UUID, Integer> quantities, Instant expiresAt) {
        return reservationService.reserve(holderId, quantities, expiresAt);
    }

    /**
     * 持ち主の引当を解く。なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    public void release(UUID holderId) {
        reservationService.release(holderId);
    }

    /**
     * 引当を在庫数の減算に置き換え、理由「注文」で調整履歴を書く。<br />
     * 引当の期限が切れていても、その時点で足りれば減算する。1 つでも足りなければ何も変えない。
     *
     * @param holderId    持ち主 ID（注文手続き）
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     * @return 足りないバリアントの ID。減算したら空
     */
    public Set<UUID> commit(UUID holderId, Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        return reservationService.commit(holderId, quantities, orderId, orderNumber);
    }

    /**
     * キャンセルした注文の数量を在庫数に戻し、理由「注文のキャンセル」で調整履歴を書く。
     *
     * @param quantities  バリアント ID → 数量
     * @param orderId     注文 ID
     * @param orderNumber 注文番号
     */
    public void restore(Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        reservationService.restore(quantities, orderId, orderNumber);
    }
}
