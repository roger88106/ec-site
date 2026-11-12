package com.ecsite.module.order.presentation;

import com.ecsite.api.store.CheckoutApi;
import com.ecsite.api.store.model.Checkout;
import com.ecsite.api.store.model.CreatedId;
import com.ecsite.api.store.model.PlaceOrderRequest;
import com.ecsite.api.store.model.StartCheckoutRequest;
import com.ecsite.common.presentation.CreatedResponses;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.ecsite.module.order.application.checkout.CheckoutQueryService;
import com.ecsite.module.order.application.checkout.CheckoutService;
import com.ecsite.module.order.application.checkout.OrderPlacementService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 注文手続きのコントローラー
 *
 * <p>C 側の API のうち、store.yaml のタグ checkout の操作を受ける。
 */
@RestController
@RequiredArgsConstructor
class StoreCheckoutController implements CheckoutApi {

    /**
     * 注文手続きのサービス
     */
    private final CheckoutService checkoutService;

    /**
     * 注文手続きの参照のサービス
     */
    private final CheckoutQueryService checkoutQueryService;

    /**
     * 注文の成立のサービス
     */
    private final OrderPlacementService orderPlacementService;

    /**
     * 店舗の注文 API のマッパー
     */
    private final StoreOrderMapper mapper;

    /**
     * token の会員
     */
    private final CurrentMember currentMember;

    /**
     * リクエストした会員の会員 ID
     */
    private final CurrentMemberIds currentMemberIds;

    /**
     * 注文手続きに進む
     *
     * @param cartId  カート ID
     * @param request 開始のリクエスト（確認した注文の ID の一覧）。本文がなければ null
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> startCheckout(UUID cartId, @Nullable StartCheckoutRequest request) {
        var id = checkoutService.start(cartId, currentMemberIds.require(), acknowledgedOrderIds(request));
        // ロケーション：作った注文手続きの URL
        return CreatedResponses.storeAt("/api/store/checkouts/{id}", id);
    }

    /**
     * 開始のリクエストから、確認した注文の ID を読む。本文も一覧もなければ空。
     *
     * @param request 開始のリクエスト
     * @return 確認した注文の ID
     */
    private static Set<UUID> acknowledgedOrderIds(@Nullable StartCheckoutRequest request) {
        if (request == null || request.getAcknowledgedOrderIds() == null) {
            return Set.of();
        }
        // 契約は一覧の要素の null を拒まないので、null を持てる集合にする（どの注文とも一致しない）
        return new HashSet<>(request.getAcknowledgedOrderIds());
    }

    /**
     * 注文手続きの表示
     *
     * @param checkoutId 注文手続き ID
     * @return Http 200 : {@link Checkout}
     */
    @Override
    public ResponseEntity<Checkout> getCheckout(UUID checkoutId) {
        return ResponseEntity.ok(mapper.toCheckout(checkoutQueryService.get(checkoutId, currentMemberIds.require())));
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
                currentMemberIds.require(), member));
        if (!result.created()) {
            return ResponseEntity.ok(CreatedResponses.storeId(result.orderId()));
        }
        // ロケーション：作った注文の URL
        return CreatedResponses.storeAt("/api/store/orders/{id}", result.orderId());
    }
}
