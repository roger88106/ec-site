package com.ecsite.module.reporting.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層
 */
public interface CatalogDirectory {

    /**
     * バリアントの商品 ID を求める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds);
}
