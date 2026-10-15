package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.application.OrderableVariantQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * レポートに公開する商品カタログの API（docs/backend/modules/catalog.md「他のモジュールへの公開」）。<br />
 * 注文の明細は商品 ID を持たないので、レポートはバリアントから商品を求める。
 */
@Component
@RequiredArgsConstructor
public class ReportCatalogApi {

    /**
     * バリアントの情報の参照
     */
    private final OrderableVariantQueryService orderableVariantQueryService;

    /**
     * バリアントの商品 ID を返す。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    public Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds) {
        var result = new LinkedHashMap<UUID, UUID>();
        orderableVariantQueryService.findVariants(variantIds)
                .forEach((variantId, variant) -> result.put(variantId, variant.productId()));
        return result;
    }
}
