package com.ecsite.module.order.presentation;

import com.ecsite.api.admin.OrderApi;
import com.ecsite.api.admin.model.CancelOrderRequest;
import com.ecsite.api.admin.model.OrderDetail;
import com.ecsite.api.admin.model.OrderStatus;
import com.ecsite.api.admin.model.OrderSummaryPage;
import com.ecsite.api.admin.model.ShipOrderRequest;
import com.ecsite.api.admin.model.Shipment;
import org.springframework.http.HttpStatus;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.security.CurrentOperator;
import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.OrderShipmentService;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
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
     * 出荷の登録のサービス
     */
    private final OrderShipmentService orderShipmentService;

    /**
     * 管理 API（注文）のマッパー
     */
    private final AdminOrderMapper mapper;

    /**
     * 要求の token の運営者
     */
    private final CurrentOperator currentOperator;

    /**
     * 注文一覧。注文日時の新しい順
     *
     * @param orderNumber 注文番号の前方一致
     * @param status      状態
     * @param orderedFrom 注文日の範囲の開始（日本時間）
     * @param orderedTo   注文日の範囲の終了（日本時間）
     * @param authorizationExpiring true なら、与信期限が近い注文だけ
     * @param returned    true なら、配送状態が返送の注文だけ
     * @param page        ページ番号
     * @param size        1 ページの件数
     * @return Http 200 : {@link OrderSummaryPage}
     */
    @Override
    public ResponseEntity<OrderSummaryPage> listOrders(String orderNumber, OrderStatus status, LocalDate orderedFrom,
                                                       LocalDate orderedTo, Boolean authorizationExpiring, Boolean returned,
                                                       Integer page, Integer size) {
        var result = orderQueryService.list(orderNumber,
                status == null ? null : com.ecsite.module.order.domain.order.OrderStatus.valueOf(status.getValue()),
                orderedFrom, orderedTo, PageRequest.of(page, size));
        var payments = orderQueryService.findPayments(result.items().stream().map(OrderQueryRepository.OrderSummaryView::id).toList());
        return ResponseEntity.ok(mapper.toOrderSummaryPage(result, payments));
    }

    /**
     * 注文の詳細
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link OrderDetail}
     */
    @Override
    public ResponseEntity<OrderDetail> getOrder(UUID orderId) {
        var order = orderQueryService.get(orderId);
        return ResponseEntity.ok(mapper.toOrderDetail(order, orderQueryService.findPayments(List.of(orderId)).get(orderId),
                orderQueryService.findShipments(List.of(orderId)).get(orderId)));
    }

    /**
     * 出荷の登録
     *
     * @param orderId 注文 ID
     * @param request 配送業者と送り状番号
     * @return Http 201 : {@link Shipment}
     */
    @Override
    public ResponseEntity<Shipment> shipOrder(UUID orderId, ShipOrderRequest request) {
        var shipment = orderShipmentService.ship(orderId, Carrier.valueOf(request.getCarrier().getValue()),
                request.getTrackingNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toShipment(shipment));
    }

    /**
     * キャンセルの申請の承認。キャンセル済みなら何もしない
     *
     * @param orderId 注文 ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> approveOrderCancellation(UUID orderId) {
        orderCancellationService.approve(orderId, currentOperator.require());
        return ResponseEntity.noContent().build();
    }

    /**
     * キャンセルの申請の却下
     *
     * @param orderId 注文 ID
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> rejectOrderCancellation(UUID orderId) {
        orderCancellationService.reject(orderId, currentOperator.require());
        return ResponseEntity.noContent().build();
    }

    /**
     * 運営者の判断によるキャンセル。キャンセル済みなら何もしない
     *
     * @param orderId 注文 ID
     * @param request キャンセルのリクエスト（理由は必須）
     * @return Http 204
     */
    @Override
    public ResponseEntity<Void> cancelOrder(UUID orderId, CancelOrderRequest request) {
        orderCancellationService.cancel(orderId, request.getReason(), currentOperator.require());
        return ResponseEntity.noContent().build();
    }
}
