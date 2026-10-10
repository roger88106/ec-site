package com.ecsite.module.inventory.api;

import com.ecsite.module.inventory.application.StockReservationService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * カートと注文に公開する、販売可能数と引当の API（docs/modules/inventory.md「引当」）。<br />
 * 書き込みの操作は呼び出し元のトランザクションに参加する（plan 002 第 2 節）。
 */
@Component
@RequiredArgsConstructor
public class StockReservationApi {

    /**
     * 引当のサービス
     */
    private final StockReservationService reservationService;

    /**
     * 販売可能数（在庫数 − 有効な引当の合計）を返す（spec 002 BR-11）。在庫のないバリアントは 0。
     *
     * @param variantIds       バリアント ID
     * @param excludedHolderId 数えない引当の持ち主（注文手続き中の自分の引当）。なければ null
     * @return バリアント ID → 販売可能数（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, @Nullable UUID excludedHolderId) {
        return reservationService.sellableQuantities(variantIds, excludedHolderId);
    }

    /**
     * 全品目を引き当てる（AC-82、83）。同じ持ち主の引当は置き換える。
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
     * 持ち主の引当を解く（AC-84）。なければ何もしない。
     *
     * @param holderId 持ち主 ID
     */
    public void release(UUID holderId) {
        reservationService.release(holderId);
    }
}
