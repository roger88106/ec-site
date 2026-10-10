package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.OrderApi;
import com.ecsite.api.admin.model.CancelOrderRequest;
import com.ecsite.api.admin.model.OrderDetail;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.OrderSummaryPage;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 注文のコントローラー（B 側。admin.yaml の tag: order）
 */
@RestController
@RequiredArgsConstructor
class AdminOrderController implements OrderApi {

    /**
     * キャンセルのサービス
     */
    private final OrderCancellationService orderCancellationService;

    /**
     * 注文の照会のサービス
     */
    private final OrderQueryService orderQueryService;

    /**
     * 管理 API（注文）のマッパー
     */
    private final AdminOrderMapper mapper;

    /**
     * 注文一覧（AC-97〜99、106）。注文日時の新しい順
     *
     * @param orderNumber 注文番号の前方一致
     * @param status      状態
     * @param orderedFrom 注文日の範囲の開始（日本時間）
     * @param orderedTo   注文日の範囲の終了（日本時間）
     * @param page        ページ番号
     * @param size        1 ページの件数
     * @return Http 200 : {@link OrderSummaryPage}
     */
    @Override
    public ResponseEntity<OrderSummaryPage> listOrders(String orderNumber, OrderStatus status, LocalDate orderedFrom,
                                                       LocalDate orderedTo, Integer page, Integer size) {
        var result = orderQueryService.list(orderNumber,
                status == null ? null : com.ecsite.module.order.domain.order.OrderStatus.valueOf(status.getValue()),
                orderedFrom, orderedTo, PageRequest.of(page, size));
        return ResponseEntity.ok(mapper.toOrderSummaryPage(result));
    }

    /**
     * 注文の詳細（AC-100、106）
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link OrderDetail}
     */
    @Override
    public ResponseEntity<OrderDetail> getOrder(UUID orderId) {
        return ResponseEntity.ok(mapper.toOrderDetail(orderQueryService.get(orderId)));
    }

    /**
     * キャンセルの申請の承認（AC-101、104、105）。キャンセル済みなら何もしない
     *
     * @param orderId 注文 ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> approveOrderCancellation(UUID orderId) {
        orderCancellationService.approve(orderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * キャンセルの申請の却下（AC-102、105）
     *
     * @param orderId 注文 ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> rejectOrderCancellation(UUID orderId) {
        orderCancellationService.reject(orderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 運営者の判断によるキャンセル（AC-103〜105）。キャンセル済みなら何もしない
     *
     * @param orderId 注文 ID
     * @param request キャンセルのリクエスト（理由は必須）
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> cancelOrder(UUID orderId, CancelOrderRequest request) {
        orderCancellationService.cancel(orderId, request.getReason());
        return ResponseEntity.noContent().build();
    }
}
