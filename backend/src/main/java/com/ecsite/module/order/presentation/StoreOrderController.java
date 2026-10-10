package com.ecsite.module.order.presentation;

import com.ecsite.api.store.OrderApi;
import com.ecsite.api.store.model.Order;
import com.ecsite.api.store.model.RequestOrderCancellationRequest;
import com.ecsite.module.order.application.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * 店舗 API（注文手続き・注文）のマッパー
     */
    private final StoreOrderMapper mapper;

    /**
     * 注文の表示（AC-93、94、96）
     *
     * @param orderId 注文 ID
     * @return Http 200 : {@link Order}
     */
    @Override
    public ResponseEntity<Order> getOrder(UUID orderId) {
        return ResponseEntity.ok(mapper.toOrder(orderQueryService.get(orderId)));
    }

    /**
     * キャンセルの申請（AC-95、96）。段階 4 の T2404 で実装する
     *
     * @param orderId 注文 ID
     * @param request キャンセルの申請のリクエスト
     * @return Http 501
     */
    @Override
    public ResponseEntity<Void> requestOrderCancellation(UUID orderId, RequestOrderCancellationRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
