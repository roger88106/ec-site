package com.ecsite.module.inventory.api;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.application.StockQueryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在庫の有無の API
 *
 * <p>在庫数そのものは返さず、有無だけを返す。
 */
@Component
@RequiredArgsConstructor
public class StockAvailabilityApi {

    /**
     * 在庫のクエリサービス
     */
    private final StockQueryService queryService;

    /**
     * 在庫の有無を返す。
     *
     * <p>販売可能数（在庫数 − 有効な引当）が 1 以上なら「あり」。在庫が作られていないバリアントは「なし」とする。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 在庫があれば true（指定したすべてのバリアントを含む）
     */
    public Map<VariantId, Boolean> availabilityOf(@NonNull Collection<VariantId> variantIds) {
        var sellable = queryService.sellableQuantities(variantIds, null);
        var availability = new LinkedHashMap<VariantId, Boolean>();
        sellable.forEach((variantId, quantity) -> availability.put(variantId, quantity > 0));
        return Collections.unmodifiableMap(availability);
    }
}
