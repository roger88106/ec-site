package com.ecsite.module.order.presentation;

import com.ecsite.api.store.OrderApi;
import com.ecsite.api.store.model.Order;
import com.ecsite.api.store.model.OrderSummaryPage;
import com.ecsite.api.store.model.RequestOrderCancellationRequest;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.api.MemberApi;
import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 注文のコントローラー（C 側。store.yaml の tag: order）
 */
@RestController
@RequiredArgsConstructor
class StoreOrderController implements OrderApi {

    /**
     * 注文の照会のサービス
     */
    private final OrderQueryService orderQueryService;

    /**
     * キャンセルのサービス
     */
    private final OrderCancellationService orderCancellationService;

    /**
     * 店舗 API（注文手続き・注文）のマッパー
     */
    private final StoreOrderMapper mapper;

    /**
     * token の会員
     */
    private final CurrentMember currentMember;

    /**
     * 会員の API（会員 ID を求める）
     */
    private final MemberApi memberApi;

    /**
     * 注文の表示（AC-93、94、96）
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link Order}
     */
    @Override
    public ResponseEntity<Order> getOrder(UUID orderId) {
        return ResponseEntity.ok(mapper.toOrder(orderQueryService.getForMember(orderId, memberId())));
    }

    /**
     * キャンセルの申請（AC-95、96）。申請中なら何もしない
     *
     * @param orderId 注文 ID
     * @param request キャンセルの申請のリクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> requestOrderCancellation(UUID orderId, RequestOrderCancellationRequest request) {
        orderCancellationService.request(orderId, memberId(), request.getReason().orElse(null));
        return ResponseEntity.noContent().build();
    }

    /**
     * 自分の注文の一覧（AC-129）。注文日時の新しい順
     *
     * @param page ページ番号
     * @param size 1 ページの件数
     * @return Http 200 : {@link OrderSummaryPage}
     */
    @Override
    public ResponseEntity<OrderSummaryPage> listMemberOrders(Integer page, Integer size) {
        var result = orderQueryService.listForMember(memberId(), PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toOrderSummaryPage(result));
    }

    /**
     * token の会員の ID（会員の操作。token がなければ 401）
     */
    private UUID memberId() {
        return memberApi.memberIdOf(currentMember.require().subject());
    }
}
