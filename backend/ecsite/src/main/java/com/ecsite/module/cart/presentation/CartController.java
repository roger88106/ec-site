package com.ecsite.module.cart.presentation;

import com.ecsite.api.store.CartApi;
import com.ecsite.api.store.model.AddCartItemRequest;
import com.ecsite.api.store.model.ChangeCartItemQuantityRequest;
import com.ecsite.api.store.model.CreatedId;
import com.ecsite.api.store.model.MemberCart;
import com.ecsite.api.store.model.MergeCartRequest;
import com.ecsite.api.store.model.MergeCartResult;
import com.ecsite.api.store.model.MergedCartItem;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.domain.CartMergeResult;
import com.ecsite.module.member.api.MemberApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * カートのコントローラー（C 側。store.yaml の tag: cart）。<br />
 * カートの表示（tag: cart-view）は、店舗モジュールが受ける。<br />
 * カートの操作は認証が任意で、token があれば会員として扱う。会員のカートは持ち主だけが使える（AC-121）。
 */
@RestController
@RequiredArgsConstructor
class CartController implements CartApi {

    /**
     * カートのサービス
     */
    private final CartService cartService;

    /**
     * token の会員
     */
    private final CurrentMember currentMember;

    /**
     * 会員の API（会員 ID を求める）
     */
    private final MemberApi memberApi;

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
        cartService.addItem(cartId, requester(), request.getVariantId(), request.getQuantity());
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
        cartService.changeQuantity(cartId, requester(), variantId, request.getQuantity());
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
        cartService.removeItem(cartId, requester(), variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 会員のカート（AC-121）。なければ作る
     *
     * @return Http 200 : {@link MemberCart}
     */
    @Override
    public ResponseEntity<MemberCart> getMemberCart() {
        return ResponseEntity.ok(new MemberCart(cartService.memberCartId(memberId())));
    }

    /**
     * カートを移す（AC-122、123）
     *
     * @param request カートを移すリクエスト
     * @return Http 200 : {@link MergeCartResult}
     */
    @Override
    public ResponseEntity<MergeCartResult> mergeCart(MergeCartRequest request) {
        var merged = cartService.merge(memberId(), request.getCartId());
        return ResponseEntity.ok(new MergeCartResult(
                merged.cartId(),
                merged.result().truncatedItems().stream().map(CartController::toMergedItem).toList(),
                merged.result().skippedItems().stream().map(CartController::toMergedItem).toList()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 要求した会員の ID。ログインしていなければ null
     */
    private UUID requester() {
        return currentMember.find().map(member -> memberApi.memberIdOf(member.subject())).orElse(null);
    }

    /**
     * token の会員の ID（会員の操作。token がなければ 401）
     */
    private UUID memberId() {
        return memberApi.memberIdOf(currentMember.require().subject());
    }

    /**
     * 移した結果の品目を、レスポンスの品目にする。
     */
    private static MergedCartItem toMergedItem(CartMergeResult.Item item) {
        return new MergedCartItem(item.variantId(), item.quantity());
    }
}
