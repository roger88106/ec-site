package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.checkout.CheckoutId;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 在庫の腐敗防止層
 *
 * <p>在庫モジュールの API を呼ぶ。引当の持ち主には、注文手続きの ID を、引当の持ち主の識別子に変換して渡す。
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
    public Map<VariantId, Integer> sellableQuantities(Collection<VariantId> variantIds, @Nullable CheckoutId excludedHolderId) {
        var holderId = excludedHolderId == null ? null : toHolderId(excludedHolderId);
        return stockReservationApi.sellableQuantities(variantIds, holderId);
    }

    /**
     * 在庫を引き当てる。
     */
    @Override
    public Set<VariantId> reserve(CheckoutId holderId, Map<VariantId, Integer> quantities, Instant expiresAt) {
        return stockReservationApi.reserve(toHolderId(holderId), quantities, expiresAt);
    }

    /**
     * 引当をまとめて解く。
     */
    @Override
    public void release(Collection<CheckoutId> holderIds) {
        stockReservationApi.release(holderIds.stream().map(StockApiDirectory::toHolderId).toList());
    }

    /**
     * 引当を確定して出庫する。
     */
    @Override
    public Set<VariantId> commit(CheckoutId holderId, Map<VariantId, Integer> quantities, OrderId orderId, String orderNumber) {
        return stockReservationApi.commit(toHolderId(holderId), quantities, orderId, orderNumber);
    }

    /**
     * 出庫した在庫を戻す。
     */
    @Override
    public void restore(Map<VariantId, Integer> quantities, OrderId orderId, String orderNumber) {
        stockReservationApi.restore(quantities, orderId, orderNumber);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文手続きの ID を、引当の持ち主の識別子に変換する。
     *
     * @param checkoutId 注文手続き ID
     * @return 引当の持ち主の識別子（値は注文手続き ID と同じ）
     */
    private static ReservationHolderId toHolderId(CheckoutId checkoutId) {
        return new ReservationHolderId(checkoutId.value());
    }
}
