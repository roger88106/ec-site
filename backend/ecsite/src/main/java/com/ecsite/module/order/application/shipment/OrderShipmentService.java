package com.ecsite.module.order.application.shipment;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.model.OrderShipment;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.domain.order.Carrier;
import com.ecsite.module.order.domain.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 出荷の登録のサービス（docs/design/modules/order.md「出荷の登録」）。<br />
 * 出荷できるかは、注文の参照の service（{@link OrderQueryService}）の運営者用の詳細で確かめ、注文の Repository には依存しない。<br />
 * 売上確定は決済サービスを呼ぶので、トランザクションを持たずに先に行い、注文と出荷の変更だけを
 * {@link OrderShipmentCommandService} のトランザクションで行う（決済サービスの応答を待つ間、注文の行ロックを持たない）。
 */
@Service
@RequiredArgsConstructor
public class OrderShipmentService {

    /**
     * 注文の照会のサービス（出荷できるかは、運営者用の詳細の操作の可否で判定する）
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
     * 出荷を登録する。受付の注文の売上を確定してから、注文を出荷済みにし、出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 作った出荷
     * @throws BusinessException 注文がない、受付でない（売上確定の後に変わった場合を含む）、与信の期限が切れている
     * @throws com.ecsite.common.error.exception.SystemException 決済サービスに接続できない（出荷は作らない）
     */
    public OrderShipment ship(UUID orderId, Carrier carrier, String trackingNumber) {
        // 注文がなければ、照会のサービスが NOT_FOUND にする。出荷できなければ、売上を確定しない
        if (!orderQueryService.get(orderId).actions().shippable()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_SHIPPABLE);
        }
        paymentDirectory.capture(orderId);
        return commandService.ship(orderId, carrier, trackingNumber);
    }
}
