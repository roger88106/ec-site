package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.catalog.api.CatalogApi;
import com.ecsite.module.reporting.application.model.CatalogProduct;
import com.ecsite.module.reporting.application.model.CatalogVariant;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層
 *
 * <p>商品カタログモジュールの API を呼び、レポートの型に変換する。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * 商品カタログの API
     */
    private final CatalogApi catalogApi;

    /**
     * バリアントの商品 ID を、商品カタログの API で求める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    @Override
    public Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds) {
        var result = new LinkedHashMap<UUID, UUID>();
        catalogApi.findVariants(variantIds).forEach((variantId, variant) -> result.put(variantId, variant.productId()));
        return result;
    }

    /**
     * 商品とバリアントの一覧を、商品カタログの API で読み、レポートの型にする。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    @Override
    public Optional<CatalogProduct> findProduct(UUID productId) {
        return catalogApi.findProduct(productId).map(product -> new CatalogProduct(product.productId(), product.name(),
                product.variants().stream().map(variant -> new CatalogVariant(variant.variantId(), variant.variantCode(),
                                variant.axisValues().stream()
                                        .map(axis -> new ReportOrder.AxisValue(axis.name(), axis.value()))
                                        .toList()))
                        .toList()));
    }
}
