package com.ecsite.module.storefront.controller;

import com.ecsite.api.store.ShopApi;
import com.ecsite.api.store.model.Shop;
import com.ecsite.module.storefront.service.ShopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店舗の情報のコントローラー（C 側。store.yaml の tag: shop）
 */
@RestController
@RequiredArgsConstructor
class StorefrontShopController implements ShopApi {

    /**
     * 店舗の情報の設定
     */
    private final ShopProperties shopProperties;

    /**
     * 店舗 API のマッパー
     */
    private final StorefrontMapper mapper;

    /**
     * 店舗の情報（AC-96）
     *
     * @return Http 200 : {@link Shop}
     */
    @Override
    public ResponseEntity<Shop> getShop() {
        return ResponseEntity.ok(mapper.toShop(shopProperties));
    }
}
