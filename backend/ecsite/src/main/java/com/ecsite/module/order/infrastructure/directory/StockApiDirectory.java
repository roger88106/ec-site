package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.order.application.port.StockDirectory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 在庫の腐敗防止層
 *
 * <p>在庫モジュールの API を呼ぶ。引当の持ち主には、注文手続きか注文の ID を渡す。
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
        var holderId = excludedHolderId == null ? null : new ReservationHolderId(excludedHolderId);
        var sellable = stockReservationApi.sellableQuantities(variantIds.stream().map(VariantId::new).toList(), holderId);
        return toUuidKeys(sellable);
    }

    /**
     * 在庫を引き当てる。
     */
    @Override
    public Set<UUID> reserve(UUID holderId, Map<UUID, Integer> quantities, Instant expiresAt) {
        return toUuids(stockReservationApi.reserve(new ReservationHolderId(holderId), toVariantKeys(quantities), expiresAt));
    }

    /**
     * 引当をまとめて解く。
     */
    @Override
    public void release(Collection<UUID> holderIds) {
        stockReservationApi.release(holderIds.stream().map(ReservationHolderId::new).toList());
    }

    /**
     * 引当を確定して出庫する。
     */
    @Override
    public Set<UUID> commit(UUID holderId, Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        return toUuids(stockReservationApi.commit(
                new ReservationHolderId(holderId), toVariantKeys(quantities), new OrderId(orderId), orderNumber));
    }

    /**
     * 出庫した在庫を戻す。
     */
    @Override
    public void restore(Map<UUID, Integer> quantities, UUID orderId, String orderNumber) {
        stockReservationApi.restore(toVariantKeys(quantities), new OrderId(orderId), orderNumber);
    }

// ============================== プライベートメソッド ==============================

    /**
     * キーの UUID を、バリアントの識別子に変換する（順を保つ）。
     *
     * @param quantities バリアント ID → 数量
     * @return バリアントの識別子 → 数量
     */
    private static Map<VariantId, Integer> toVariantKeys(Map<UUID, Integer> quantities) {
        var converted = new LinkedHashMap<VariantId, Integer>();
        quantities.forEach((variantId, quantity) -> converted.put(new VariantId(variantId), quantity));
        return converted;
    }

    /**
     * キーのバリアントの識別子を、UUID に変換する（順を保つ）。
     *
     * @param quantities バリアントの識別子 → 数量
     * @return バリアント ID → 数量
     */
    private static Map<UUID, Integer> toUuidKeys(Map<VariantId, Integer> quantities) {
        var converted = new LinkedHashMap<UUID, Integer>();
        quantities.forEach((variantId, quantity) -> converted.put(variantId.value(), quantity));
        return converted;
    }

    /**
     * バリアントの識別子を、UUID に変換する（順を保つ）。
     *
     * @param variantIds バリアントの識別子
     * @return バリアント ID
     */
    private static Set<UUID> toUuids(Set<VariantId> variantIds) {
        var converted = new LinkedHashSet<UUID>();
        variantIds.forEach(variantId -> converted.add(variantId.value()));
        return converted;
    }
}
