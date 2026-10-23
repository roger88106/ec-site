package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.order.application.port.StockDirectory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 在庫の腐敗防止層の実装。在庫モジュールの API（{@link StockReservationApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class StockApiDirectory implements StockDirectory {

    /**
     * 販売可能数と引当の API
     */
    private final StockReservationApi stockReservationApi;

    /**
     * バリアントの販売可能数を返す。
     */
    @Override
    public Map<UUID, Integer> sellableQuantities(Collection<UUID> variantIds, @Nullable UUID excludedHolderId) {
        return stockReservationApi.sellableQuantities(variantIds, excludedHolderId);
    }

    /**
     * 在庫を引き当てる。
     */
    @Override
    public Set<UUID> reserve(UUID holderId, Map<UUID, Integer> quantities, Instant expiresAt) {
        return stockReservationApi.reserve(holderId, quantities, expiresAt);
    }

    /**
     * 引当を解放する。
     */
    @Override
    public void release(UUID holderId) {
        stockReservationApi.release(holderId);
    }

    /**
     * 引当を確定して出庫する。
     */
    @Override
    public Set<UUID> commit(UUID holderId, Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        return stockReservationApi.commit(holderId, quantities, orderId, orderNumber);
    }

    /**
     * 出庫した在庫を戻す。
     */
    @Override
    public void restore(Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        stockReservationApi.restore(quantities, orderId, orderNumber);
    }
}
