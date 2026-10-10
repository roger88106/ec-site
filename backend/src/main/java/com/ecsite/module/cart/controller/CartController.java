package com.ecsite.module.cart.controller;

import com.ecsite.api.store.CartApi;
import com.ecsite.api.store.model.AddCartItemRequest;
import com.ecsite.api.store.model.ChangeCartItemQuantityRequest;
import com.ecsite.api.store.model.CreatedId;
import com.ecsite.module.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * カートのコントローラー（C 側。store.yaml の tag: cart）。<br />
 * カートの表示（tag: cart-view）は、店舗モジュールが受ける。
 */
@RestController
@RequiredArgsConstructor
class CartController implements CartApi {

    /**
     * カートのサービス
     */
    private final CartService cartService;

    /**
     * カートを作る（AC-73）
     *
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createCart() {
        var id = cartService.createCart();

        // ロケーション：作ったカートの URL
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(new CreatedId(id));
    }

    /**
     * カートに入れる（AC-72、74〜76）。品目はカートの一部で単独の URL を持たないので、Location は返さない
     *
     * @param cartId  カート ID
     * @param request カートに入れるリクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> addCartItem(UUID cartId, AddCartItemRequest request) {
        cartService.addItem(cartId, request.getVariantId(), request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * 品目の数量の変更（AC-75、76、78）
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param request   数量の変更リクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> changeCartItemQuantity(UUID cartId, UUID variantId, ChangeCartItemQuantityRequest request) {
        cartService.changeQuantity(cartId, variantId, request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * 品目の削除（AC-78）。カートにない品目でも 204
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> removeCartItem(UUID cartId, UUID variantId) {
        cartService.removeItem(cartId, variantId);
        return ResponseEntity.noContent().build();
    }
}
