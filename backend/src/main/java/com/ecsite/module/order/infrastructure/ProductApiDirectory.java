package com.ecsite.module.order.infrastructure;

import com.ecsite.module.catalog.api.OrderCatalogApi;
import com.ecsite.module.catalog.api.OrderableVariant;
import com.ecsite.module.order.domain.port.ProductDirectory;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.VariantInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の実装。商品カタログの API（{@link OrderCatalogApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class ProductApiDirectory implements ProductDirectory {

    /**
     * カート・注文向けの商品カタログの API
     */
    private final OrderCatalogApi orderCatalogApi;

    @Override
    public Map<UUID, VariantInfo> findVariants(Collection<UUID> variantIds) {
        var result = new LinkedHashMap<UUID, VariantInfo>();
        orderCatalogApi.findVariants(variantIds).forEach((variantId, variant) -> result.put(variantId, toInfo(variant)));
        return result;
    }

    /**
     * 商品カタログの型を、注文モジュールの型に変換する。
     */
    private static VariantInfo toInfo(OrderableVariant variant) {
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
