package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.api.model.OrderableVariant;
import com.ecsite.module.catalog.application.OrderableVariantQueryService;
import com.ecsite.module.catalog.application.model.view.OrderableVariantView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 注文に使うバリアントの API
 *
 * <p>表示されない商品・販売停止のバリアントも返す。表示と販売停止の状態は項目で返す。
 */
@Component
@RequiredArgsConstructor
public class OrderCatalogApi {

    /**
     * 注文に使うバリアントの情報の参照
     */
    private final OrderableVariantQueryService queryService;

    /**
     * 複数のバリアントについて、注文に使う情報を返す。
     *
     * @param variantIds バリアント ID
     * @return バリアント ID → 情報。存在しないバリアントは含めない
     */
    public Map<UUID, OrderableVariant> findVariants(@NonNull Collection<UUID> variantIds) {
        var result = new LinkedHashMap<UUID, OrderableVariant>();
        queryService.findVariants(variantIds).forEach((variantId, view) -> result.put(variantId, toVariant(view)));
        return result;
    }

    /**
     * application の view を公開の型に変換する。
     */
    private static OrderableVariant toVariant(OrderableVariantView view) {
        return new OrderableVariant(view.variantId(), view.productId(), view.productCode(), view.variantCode(),
                view.productName(),
                view.axisValues().stream().map(axis -> new OrderableVariant.AxisValue(axis.name(), axis.value())).toList(),
                view.priceExclTax(), view.taxCategoryId(), view.imageUrl(), view.visible(), view.salesSuspended());
    }
}
