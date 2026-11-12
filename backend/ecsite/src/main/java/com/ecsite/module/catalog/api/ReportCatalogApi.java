package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.api.model.ReportProduct;
import com.ecsite.module.catalog.application.OrderableVariantQueryService;
import com.ecsite.module.catalog.application.model.view.ProductDetailView;
import com.ecsite.module.catalog.application.product.ProductQueryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 商品とバリアントの対応の API
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
    public Map<UUID, UUID> productIdsOf(@NonNull Collection<UUID> variantIds) {
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
    public Optional<ReportProduct> findProduct(@NonNull UUID productId) {
        return productQueryService.findProduct(productId).map(ReportCatalogApi::toProduct);
    }

    /**
     * application の view を公開の型に変換する。
     */
    private static ReportProduct toProduct(ProductDetailView product) {
        var variants = product.variants().stream()
                .map(variant -> toVariant(product.axes(), variant))
                .toList();
        return new ReportProduct(product.id(), product.name(), variants);
    }

    /**
     * バリアントを公開の型に変換する。軸の名前と、バリアントの軸の値を、並びの位置で組にする。
     */
    private static ReportProduct.Variant toVariant(List<String> axes, ProductDetailView.VariantView variant) {
        var axisValues = IntStream.range(0, axes.size())
                .mapToObj(i -> new ReportProduct.AxisValue(axes.get(i), variant.axisValues().get(i)))
                .toList();
        return new ReportProduct.Variant(variant.id(), variant.variantCode(), axisValues);
    }
}
