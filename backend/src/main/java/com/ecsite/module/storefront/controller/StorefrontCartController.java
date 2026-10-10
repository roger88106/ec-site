package com.ecsite.module.storefront.controller;

import com.ecsite.api.store.CartViewApi;
import com.ecsite.api.store.model.Cart;
import com.ecsite.module.storefront.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 店舗のカートの表示のコントローラー（C 側。store.yaml の tag: cart-view）
 */
@RestController
@RequiredArgsConstructor
class StorefrontCartController implements CartViewApi {

    /**
     * 店舗のサービス
     */
    private final StorefrontService storefrontService;

    /**
     * 店舗 API のマッパー
     */
    private final StorefrontMapper mapper;

    /**
     * カートの表示（AC-77、79〜81）
     *
     * @param cartId カート ID
     * @return Http 200 : {@link Cart}
     */
    @Override
    public ResponseEntity<Cart> getCart(UUID cartId) {
        return ResponseEntity.ok(mapper.toCart(storefrontService.getCart(cartId)));
    }
}
