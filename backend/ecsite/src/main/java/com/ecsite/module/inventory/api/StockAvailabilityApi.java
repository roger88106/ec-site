package com.ecsite.module.inventory.api;

import com.ecsite.module.inventory.application.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 店舗向けの在庫の API。複数のバリアントについて、在庫の有無を返す（docs/modules/inventory.md）。<br />
 * 在庫数そのものは返さない。C 側では有無だけを表示するため。
 */
@Component
@RequiredArgsConstructor
public class StockAvailabilityApi {

    /**
     * 引当のサービス（販売可能数）
     */
    private final StockReservationService reservationService;

    /**
     * 在庫の有無を返す。販売可能数（在庫数 − 有効な引当。spec 002 BR-11）が 1 以上なら「あり」。<br />
     * 在庫がまだないバリアント（登録のイベントの処理前など）は「なし」とする。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 在庫があれば true（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Boolean> availabilityOf(Collection<UUID> variantIds) {
        var sellable = reservationService.sellableQuantities(variantIds, null);
        var availability = new LinkedHashMap<UUID, Boolean>();
        sellable.forEach((variantId, quantity) -> availability.put(variantId, quantity > 0));
        return Collections.unmodifiableMap(availability);
    }
}
