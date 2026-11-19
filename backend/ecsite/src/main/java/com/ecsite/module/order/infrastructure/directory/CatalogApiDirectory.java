package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.module.catalog.api.CatalogApi;
import com.ecsite.module.catalog.api.model.VariantDetail;
import com.ecsite.module.order.application.port.CatalogDirectory;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.VariantInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層
 *
 * <p>商品カタログモジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * 商品カタログの API
     */
    private final CatalogApi catalogApi;

    /**
     * バリアントの情報を、商品カタログの API から取って変換する。
     */
    @Override
    public Map<UUID, VariantInfo> findVariants(Collection<UUID> variantIds) {
        var result = new LinkedHashMap<UUID, VariantInfo>();
        catalogApi.findVariants(variantIds).forEach((variantId, variant) -> result.put(variantId, toInfo(variant)));
        return result;
    }

    /**
     * 商品カタログの型を、注文モジュールの型に変換する。
     */
    private static VariantInfo toInfo(VariantDetail variant) {
        return new VariantInfo(
                variant.variantId(),
                variant.productId(),
                variant.productCode(),
                variant.variantCode(),
                variant.productName(),
                variant.axisValues().stream().map(axis -> new AxisValue(axis.name(), axis.value())).toList(),
                variant.priceExclTax(),
                variant.taxCategoryId(),
                variant.imageUrl(),
                variant.visible(),
                variant.salesSuspended());
    }
}
