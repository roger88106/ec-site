package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.ShopApi;
import com.ecsite.api.store.model.Shop;
import com.ecsite.module.storefront.application.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店舗の情報のコントローラー
 *
 * <p>C 側の API のうち、store.yaml のタグ shop の操作を受ける。
 */
@RestController
@RequiredArgsConstructor
class StorefrontShopController implements ShopApi {

    /**
     * 店舗のサービス
     */
    private final StorefrontService storefrontService;

    /**
     * 店舗 API のマッパー
     */
    private final StorefrontMapper mapper;

    /**
     * 店舗の情報
     *
     * @return Http 200 : {@link Shop}
     */
    @Override
    public ResponseEntity<Shop> getShop() {
        return ResponseEntity.ok(mapper.toShop(storefrontService.getShop()));
    }
}
