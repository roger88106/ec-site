package com.ecsite.module.order.application.payment;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.domain.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 注文の支払画面のサービス（docs/design/modules/order.md「支払」）。<br />
 * 決済サービスを呼ぶので、トランザクションを持たない（支払画面を作る間、注文の行ロックを持たない）。
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentPageService {

    /**
     * 注文の照会のサービス
     */
    private final OrderQueryService orderQueryService;

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 会員の注文の支払画面の URL を返す。注文した会員でなければ 404。
     *
     * @param orderId  注文 ID
     * @param memberId 要求した会員の ID
     * @return 支払画面の URL
     * @throws BusinessException 支払待ちでない、支払の期限の後（{@link OrderErrorCode#ORDER_NOT_PAYABLE}）
     */
    public String pageUrl(UUID orderId, UUID memberId) {
        var order = orderQueryService.getForMember(orderId, memberId);
        if (!order.awaitingPayment()) {
            throw BusinessException.of(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        return paymentDirectory.pageUrl(orderId).orElseThrow(() -> BusinessException.of(OrderErrorCode.ORDER_NOT_PAYABLE));
    }
}
