package com.ecsite.module.order.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.shipment.OrderShipmentCommandService;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.Carrier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 注文の決済ゲートウェイサービス
 *
 * <p>決済サービスを呼ぶ注文の操作を扱う。
 *
 * <p>トランザクションを持たない。決済サービスの応答を待つ間に、注文の行ロックと DB の接続を持たないため。
 * 注文の読み取りと変更は、参照と更新のサービスのトランザクションで行う。
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentGatewayService {

    /**
     * 注文の照会のサービス
     */
    private final OrderQueryService orderQueryService;

    /**
     * 出荷の登録の更新のサービス
     */
    private final OrderShipmentCommandService shipmentCommandService;

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 会員の注文の支払画面の URL を返す。
     *
     * @param orderId  注文 ID
     * @param memberId リクエストした会員の ID
     * @return 支払画面の URL
     * @throws BusinessException 注文がない（{@code NOT_FOUND}）
     * @throws BusinessException 注文した会員でない（{@code NOT_FOUND}）
     * @throws BusinessException 支払待ちでない（{@code ORDER_NOT_PAYABLE}）
     * @throws BusinessException 支払の期限の後（{@code ORDER_NOT_PAYABLE}）
     */
    public String pageUrl(@NonNull UUID orderId, @NonNull UUID memberId) {
        var order = orderQueryService.getForMember(orderId, memberId);
        if (!order.awaitingPayment()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        return paymentDirectory.pageUrl(orderId).orElseThrow(() -> BusinessException.of(OrderErrorCode.ORDER_NOT_PAYABLE));
    }

    /**
     * 出荷を登録する。
     *
     * <p>受付の注文の売上を確定してから、注文を出荷済みにし、出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 作った出荷
     * @throws BusinessException 注文がない、または支払がない（{@code NOT_FOUND}）
     * @throws BusinessException 受付でない（{@code ORDER_NOT_SHIPPABLE}）
     * @throws BusinessException 与信の期限が切れている（{@code AUTHORIZATION_EXPIRED}）
     * @throws BusinessException 売上を確定できる状態でない（{@code PAYMENT_NOT_CAPTURABLE}）
     * @throws BusinessException 送り状番号が形に合わない（{@code TRACKING_NUMBER_INVALID}）
     * @throws BusinessException 同じ注文にすでに出荷がある（{@code SHIPMENT_ALREADY_EXISTS}）
     */
    public OrderShipment ship(@NonNull UUID orderId, @NonNull Carrier carrier, @NonNull String trackingNumber) {
        // 出荷できるかの確認 ※出荷できなければ、売上を確定しない
        if (!orderQueryService.get(orderId).actions().shippable()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_SHIPPABLE);
        }

        // 売上確定と出荷登録 ※売上確定の後に受付でなくなったときは、支払は売上確定済みのまま残る
        paymentDirectory.capture(orderId);
        return shipmentCommandService.ship(orderId, carrier, trackingNumber);
    }
}
