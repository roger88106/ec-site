package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.CartViewApi;
import com.ecsite.api.store.model.Cart;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.ecsite.module.storefront.application.StorefrontService;
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
     * 要求した会員の会員 ID
     */
    private final CurrentMemberIds currentMemberIds;

    /**
     * カートの表示
     *
     * @param cartId カート ID
     * @return Http 200 : {@link Cart}
     */
    @Override
    public ResponseEntity<Cart> getCart(UUID cartId) {
        var requester = currentMemberIds.find().orElse(null);
        return ResponseEntity.ok(mapper.toCart(storefrontService.getCart(cartId, requester)));
    }
}
