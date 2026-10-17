package com.ecsite.module.cart.presentation;

import com.ecsite.api.store.CartApi;
import com.ecsite.api.store.model.AddCartItemRequest;
import com.ecsite.api.store.model.ChangeCartItemQuantityRequest;
import com.ecsite.api.store.model.CreatedId;
import com.ecsite.api.store.model.MemberCart;
import com.ecsite.api.store.model.MergeCartRequest;
import com.ecsite.api.store.model.MergeCartResult;
import com.ecsite.common.presentation.CreatedResponses;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.member.api.CurrentMemberIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * カートのコントローラー（C 側。store.yaml の tag: cart）。<br />
 * カートの表示（tag: cart-view）は、店舗モジュールが受ける。<br />
 * カートの操作は認証が任意で、token があれば会員として扱う。会員のカートは持ち主だけが使える。
 */
@RestController
@RequiredArgsConstructor
class CartController implements CartApi {

    /**
     * カートのサービス
     */
    private final CartService cartService;

    /**
     * 要求した会員の会員 ID
     */
    private final CurrentMemberIds currentMemberIds;

    /**
     * API の型への変換
     */
    private final CartMapper mapper;

    /**
     * カートを作る
     *
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createCart() {
        var id = cartService.createCart();

        // ロケーション：作ったカートの URL
        return CreatedResponses.store(id);
    }

    /**
     * カートに入れる。品目はカートの一部で単独の URL を持たないので、Location は返さない
     *
     * @param cartId  カート ID
     * @param request カートに入れるリクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> addCartItem(UUID cartId, AddCartItemRequest request) {
        cartService.addItem(cartId, currentMemberIds.find().orElse(null), request.getVariantId(), request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * 品目の数量の変更
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param request   数量の変更リクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> changeCartItemQuantity(UUID cartId, UUID variantId, ChangeCartItemQuantityRequest request) {
        cartService.changeQuantity(cartId, currentMemberIds.find().orElse(null), variantId, request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * 品目の削除。カートにない品目でも 204
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> removeCartItem(UUID cartId, UUID variantId) {
        cartService.removeItem(cartId, currentMemberIds.find().orElse(null), variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 会員のカート。なければ作る
     *
     * @return Http 200 : {@link MemberCart}
     */
    @Override
    public ResponseEntity<MemberCart> getMemberCart() {
        return ResponseEntity.ok(mapper.toMemberCart(cartService.memberCartId(currentMemberIds.require())));
    }

    /**
     * カートを移す
     *
     * @param request カートを移すリクエスト
     * @return Http 200 : {@link MergeCartResult}
     */
    @Override
    public ResponseEntity<MergeCartResult> mergeCart(MergeCartRequest request) {
        var merged = cartService.merge(currentMemberIds.require(), request.getCartId());
        return ResponseEntity.ok(mapper.toMergeCartResult(merged));
    }
}
