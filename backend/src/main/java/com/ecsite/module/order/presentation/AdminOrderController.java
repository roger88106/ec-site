package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.OrderApi;
import com.ecsite.api.admin.model.CancelOrderRequest;
import com.ecsite.api.admin.model.OrderDetail;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.OrderSummaryPage;
import com.ecsite.module.order.application.OrderCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * 注文一覧（AC-97〜99、106）。T2406 で実装する
     *
     * @return Http 501
     */
    @Override
    public ResponseEntity<OrderSummaryPage> listOrders(String orderNumber, OrderStatus status, LocalDate orderedFrom,
                                                       LocalDate orderedTo, Integer page, Integer size) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * 注文の詳細（AC-100、106）。T2406 で実装する
     *
     * @param orderId 注文 ID
     * @return Http 501
     */
    @Override
    public ResponseEntity<OrderDetail> getOrder(UUID orderId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
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
