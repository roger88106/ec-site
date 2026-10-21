package com.ecsite.module.inventory.domain.stock;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jmolecules.ddd.annotation.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 引当・注文による減算で、在庫が足りるかの判定。
 */
@Service
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StockAllocation {

    /**
     * 販売可能数が求める数量より少ないバリアントを返す。
     *
     * @param requested バリアント ID → 求める数量
     * @param sellable  バリアント ID → 販売可能数。在庫のないバリアントは含まなくてよい（0 とみなす）
     * @return 足りないバリアントの ID。すべて足りれば空
     */
    public static Set<UUID> shortages(Map<UUID, Integer> requested, Map<UUID, Integer> sellable) {
        var shortages = new LinkedHashSet<UUID>();
        requested.forEach((variantId, quantity) -> {
            if (sellable.getOrDefault(variantId, 0) < quantity) {
                shortages.add(variantId);
            }
        });
        return shortages;
    }
}
