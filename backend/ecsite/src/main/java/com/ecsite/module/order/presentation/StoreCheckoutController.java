package com.ecsite.module.order.presentation;

import com.ecsite.api.store.CheckoutApi;
import com.ecsite.api.store.model.Checkout;
import com.ecsite.api.store.model.CreatedId;
import com.ecsite.api.store.model.PlaceOrderRequest;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.ecsite.module.order.application.checkout.CheckoutService;
import com.ecsite.module.order.application.checkout.OrderPlacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * 注文手続きのコントローラー（C 側。store.yaml の tag: checkout）
 */
@RestController
@RequiredArgsConstructor
class StoreCheckoutController implements CheckoutApi {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutService checkoutService;

    /**
     * 注文の成立のサービス
     */
    private final OrderPlacementService orderPlacementService;

    /**
     * 店舗 API（注文手続き・注文）のマッパー
     */
    private final StoreOrderMapper mapper;

    /**
     * token の会員
     */
    private final CurrentMember currentMember;

    /**
     * 要求した会員の会員 ID
     */
    private final CurrentMemberIds currentMemberIds;

    /**
     * 注文手続きに進む
     *
     * @param cartId カート ID
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> startCheckout(UUID cartId) {
        var id = checkoutService.start(cartId, currentMemberIds.require());

        // ロケーション：作った注文手続きの URL
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/store/checkouts/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).body(new CreatedId(id));
    }

    /**
     * 注文手続きの表示
     *
     * @param checkoutId 注文手続き ID
     * @return Http 200 : {@link Checkout}
     */
    @Override
    public ResponseEntity<Checkout> getCheckout(UUID checkoutId) {
        return ResponseEntity.ok(mapper.toCheckout(checkoutService.get(checkoutId, currentMemberIds.require())));
    }

    /**
     * 注文する。同じ注文手続きでの再送には、作成済みの注文 ID を 200 で返す
     *
     * @param checkoutId 注文手続き ID
     * @param request    注文のリクエスト
     * @return Http 201 : {@link CreatedId}（再送は Http 200）
     */
    @Override
    public ResponseEntity<CreatedId> placeOrder(UUID checkoutId, PlaceOrderRequest request) {
        var member = currentMember.require();
        var result = orderPlacementService.place(mapper.toPlaceOrderCommand(checkoutId, request,
                currentMemberIds.require(), member.email(), member.emailVerified()));
        var body = new CreatedId(result.orderId());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }

        // ロケーション：作った注文の URL
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/store/orders/{id}")
                .buildAndExpand(result.orderId())
                .toUri();

        return ResponseEntity.created(location).body(body);
    }
}
