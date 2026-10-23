package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.application.OrderableVariantQueryService;
import com.ecsite.module.catalog.application.product.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * レポートに公開する商品カタログの API（docs/design/modules/catalog.md「他のモジュールへの公開」）。<br />
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
     * 商品の参照
     */
    private final ProductQueryService productQueryService;

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

    /**
     * 商品とバリアントの一覧を返す。
     *
     * @param productId 商品 ID
     * @return 商品。なければ空
     */
    public Optional<ReportProduct> findProduct(UUID productId) {
        return productQueryService.findProduct(productId).map(product -> new ReportProduct(product.id(), product.name(),
                product.variants().stream().map(variant -> new ReportProduct.Variant(variant.id(), variant.variantCode(),
                        IntStream.range(0, product.axes().size())
                                .mapToObj(i -> new ReportProduct.AxisValue(product.axes().get(i), variant.axisValues().get(i)))
                                .toList()))
                        .toList()));
    }
}
