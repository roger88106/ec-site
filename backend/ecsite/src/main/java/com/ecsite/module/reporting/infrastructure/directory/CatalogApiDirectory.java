package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.catalog.api.ReportCatalogApi;
import com.ecsite.module.reporting.application.port.CatalogDirectory;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の実装。商品カタログモジュールの API（{@link ReportCatalogApi}）を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * レポート向けの商品カタログの API
     */
    private final ReportCatalogApi reportCatalogApi;

    /**
     * バリアントの商品 ID を、商品カタログの API で求める。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 商品 ID。存在しないバリアントは含めない
     */
    @Override
    public Map<UUID, UUID> productIdsOf(Collection<UUID> variantIds) {
        return reportCatalogApi.productIdsOf(variantIds);
    }

    /**
     * 商品とバリアントの一覧を、商品カタログの API で読み、レポートの型にする。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    @Override
    public Optional<Product> findProduct(UUID productId) {
        return reportCatalogApi.findProduct(productId).map(product -> new Product(product.productId(), product.name(),
                product.variants().stream().map(variant -> new Variant(variant.variantId(), variant.variantCode(),
                                variant.axisValues().stream()
                                        .map(axis -> new ReportOrder.AxisValue(axis.name(), axis.value()))
                                        .toList()))
                        .toList()));
    }
}
