package com.ecsite.module.order.application.shipment;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.model.view.OrderShipment;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.Carrier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 出荷の登録のサービス
 *
 * <p>売上確定は決済サービスを呼ぶので、トランザクションを持たずに先に行い、注文と出荷の変更だけを別のトランザクションで行う
 * （決済サービスの応答を待つ間、注文の行ロックを持たない）。
 */
@Service
@RequiredArgsConstructor
public class OrderShipmentService {

    /**
     * 注文の照会のサービス（出荷できるかは、詳細の操作の可否で判定する）
     */
    private final OrderQueryService orderQueryService;

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 出荷の登録の更新のサービス
     */
    private final OrderShipmentCommandService commandService;

    /**
     * 出荷を登録する。
     *
     * <p>受付の注文の売上を確定してから、注文を出荷済みにし、出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 作った出荷
     * @throws BusinessException 注文または支払がない（{@code NOT_FOUND}）
     * @throws BusinessException 受付でない（{@code ORDER_NOT_SHIPPABLE}。売上確定の後に変わった場合を含み、そのとき支払は売上確定済みのまま残る）
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

        // 売上確定と出荷登録
        paymentDirectory.capture(orderId);
        return commandService.ship(orderId, carrier, trackingNumber);
    }
}
