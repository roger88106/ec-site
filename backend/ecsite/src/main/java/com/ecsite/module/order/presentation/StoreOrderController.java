package com.ecsite.module.order.presentation;

import com.ecsite.api.store.OrderApi;
import com.ecsite.api.store.model.Order;
import com.ecsite.api.store.model.OrderSummaryPage;
import com.ecsite.api.store.model.PaymentPage;
import com.ecsite.api.store.model.RequestOrderCancellationRequest;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.payment.OrderPaymentPageService;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
     * 支払の画面のサービス
     */
    private final OrderPaymentPageService orderPaymentPageService;

    /**
     * 店舗 API（注文手続き・注文）のマッパー
     */
    private final StoreOrderMapper mapper;

    /**
     * 要求した会員の会員 ID
     */
    private final CurrentMemberIds currentMemberIds;

    /**
     * 注文の表示
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link Order}
     */
    @Override
    public ResponseEntity<Order> getOrder(UUID orderId) {
        return ResponseEntity.ok(mapper.toOrder(orderQueryService.getForMember(orderId, currentMemberIds.require())));
    }

    /**
     * キャンセルの申請。申請中なら何もしない
     *
     * @param orderId 注文 ID
     * @param request キャンセルの申請のリクエスト
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> requestOrderCancellation(UUID orderId, RequestOrderCancellationRequest request) {
        orderCancellationService.request(orderId, currentMemberIds.require(), request.getReason().orElse(null));
        return ResponseEntity.noContent().build();
    }

    /**
     * 支払の画面を開く。開いている画面があれば同じ画面
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link PaymentPage}
     */
    @Override
    public ResponseEntity<PaymentPage> openOrderPaymentPage(UUID orderId) {
        var url = orderPaymentPageService.pageUrl(orderId, currentMemberIds.require());
        return ResponseEntity.ok(new PaymentPage().url(URI.create(url)));
    }

    /**
     * 自分の注文の一覧。注文日時の新しい順
     *
     * @param page ページ番号
     * @param size 1 ページの件数
     * @return Http 200 : {@link OrderSummaryPage}
     */
    @Override
    public ResponseEntity<OrderSummaryPage> listMemberOrders(Integer page, Integer size) {
        var result = orderQueryService.listForMember(currentMemberIds.require(), PageRequest.of(page, size));
        var orderIds = result.items().stream().map(OrderQueryRepository.OrderSummaryView::id).toList();
        return ResponseEntity.ok(mapper.toOrderSummaryPage(result, orderQueryService.findPayments(orderIds),
                orderQueryService.findShipments(orderIds)));
    }
}
