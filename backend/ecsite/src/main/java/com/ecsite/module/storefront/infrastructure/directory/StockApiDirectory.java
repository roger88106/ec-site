package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.module.storefront.application.port.StockDirectory;
import com.ecsite.module.inventory.api.StockAvailabilityApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 在庫の腐敗防止層（constitution 4.1、docs/modules/storefront.md）。<br />
 * 在庫モジュールの API を呼び、在庫のあるバリアントの集合に変換する。
 */
@Component
@RequiredArgsConstructor
public class StockApiDirectory implements StockDirectory {

    /**
     * 店舗向けの在庫の API
     */
    private final StockAvailabilityApi stockAvailabilityApi;

    /**
     * 在庫のあるバリアントを返す。在庫がまだないバリアントは「在庫なし」とする（docs/modules/inventory.md）。
     *
     * @param variantIds バリアント ID
     * @return 指定したうち、在庫のあるバリアントの ID
     */
    @Override
    public Set<UUID> inStock(Collection<UUID> variantIds) {
        return stockAvailabilityApi.availabilityOf(variantIds).entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
