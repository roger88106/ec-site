package com.ecsite.module.cart.infrastructure.directory;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.cart.application.port.CatalogDirectory;
import com.ecsite.module.catalog.api.CatalogApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品カタログの腐敗防止層
 *
 * <p>商品カタログの API を呼び、カートに入れられるかの判定に変換する。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * 商品カタログの API
     */
    private final CatalogApi catalogApi;

    /**
     * バリアントを選べるか。
     *
     * <p>選べるのは、C 側に表示される商品の、販売停止でないバリアントである。
     *
     * @param variantId バリアント ID
     * @return 選べれば true。存在しないバリアントは false
     */
    @Override
    public boolean isSelectable(VariantId variantId) {
        var variant = catalogApi.findVariants(List.of(variantId)).get(variantId);
        return variant != null && variant.visible() && !variant.salesSuspended();
    }
}
