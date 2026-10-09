package com.ecsite.module.inventory.api;

import com.ecsite.module.inventory.application.StockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional(readOnly = true)
public class StockAvailabilityApi {

    /**
     * 在庫クエリリポジトリ
     */
    private final StockQueryRepository stockQueryRepository;

    /**
     * 在庫の有無を返す。在庫数が 1 以上なら「あり」。<br />
     * 在庫がまだないバリアント（登録のイベントの処理前など）は「なし」とする。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 在庫があれば true（指定したすべてのバリアントを含む）
     */
    public Map<UUID, Boolean> availabilityOf(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        var inStock = stockQueryRepository.findInStockVariantIds(variantIds);
        var availability = new LinkedHashMap<UUID, Boolean>();
        variantIds.forEach(variantId -> availability.put(variantId, inStock.contains(variantId)));
        return Collections.unmodifiableMap(availability);
    }
}
