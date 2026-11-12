package com.ecsite.module.reporting.application.port;

import com.ecsite.module.reporting.application.model.CatalogProduct;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の port
 */
public interface CatalogDirectory {

    /**
     * バリアントの商品 ID を求める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds);

    /**
     * 商品とバリアントの一覧を読む。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    Optional<CatalogProduct> findProduct(UUID productId);
}
