package com.ecsite.module.cart.infrastructure.directory;

import com.ecsite.module.cart.application.port.CatalogDirectory;
import com.ecsite.module.catalog.api.OrderCatalogApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 商品カタログの腐敗防止層（バックエンドのアーキテクチャ 第 1 節、docs/03-機能仕様/cart.md）。<br />
 * 商品カタログの API を呼び、カートに入れられるかの判定に変換する。
 */
@Component
@RequiredArgsConstructor
class CatalogApiDirectory implements CatalogDirectory {

    /**
     * カート・注文向けの商品カタログの API
     */
    private final OrderCatalogApi orderCatalogApi;

    /**
     * バリアントを選べるか（C 側に表示される商品の、販売停止でないバリアントか）。
     *
     * @param variantId バリアント ID
     * @return 選べれば true。存在しないバリアントは false
     */
    @Override
    public boolean isSelectable(UUID variantId) {
        var variant = orderCatalogApi.findVariants(List.of(variantId)).get(variantId);
        return variant != null && variant.visible() && !variant.salesSuspended();
    }
}
