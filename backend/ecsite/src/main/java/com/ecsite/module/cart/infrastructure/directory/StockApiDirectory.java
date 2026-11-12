package com.ecsite.module.cart.infrastructure.directory;

import com.ecsite.module.cart.application.port.StockDirectory;
import com.ecsite.module.inventory.api.StockReservationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 在庫の腐敗防止層
 *
 * <p>在庫モジュールの API を呼び、バリアントの販売可能数に変換する。
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
     *
     * <p>全ての注文手続きの引当を差し引く（このカートの注文手続きの引当を含む）。
     *
     * @param variantId バリアント ID
     * @return 販売可能数。在庫がなければ 0
     */
    @Override
    public int sellableQuantity(UUID variantId) {
        // カートは注文手続き（引当の持ち主）を知らないので、除く持ち主を指定せず（null）、全ての引当を差し引く
        return stockReservationApi.sellableQuantities(List.of(variantId), null).getOrDefault(variantId, 0);
    }
}
