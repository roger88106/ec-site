package com.ecsite.module.payment.application;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 支払画面のサービス
 *
 * <p>支払画面は、1 つの支払に同時に 1 つだけ開く。決済サービスはトランザクションの外で呼ぶ。
 */
@Service
@RequiredArgsConstructor
public class PaymentPageService {

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService;

    /**
     * 決済サービス
     */
    private final PaymentGateway gateway;

    /**
     * 決済の設定
     */
    private final PaymentProperties properties;

    /**
     * 支払画面の URL を返す。
     *
     * <p>開いている画面があればそれを、なければ決済サービスに作らせる。
     *
     * @param orderId 注文 ID
     * @return 支払画面の URL。支払がない、支払えない（未払いでない、期限の後）なら空
     * @throws SystemException 決済サービスに接続できない（PAYMENT_GATEWAY_UNAVAILABLE）
     */
    public Optional<String> pageUrl(@NonNull UUID orderId) {
        // 支払取得
        var payable = queryService.findPayable(orderId);
        if (payable.isEmpty()) {
            return Optional.empty();
        }
        var payment = payable.get();
        if (payment.page() != null) {
            return Optional.of(payment.page().url());
        }

        // 支払画面作成
        var opened = gateway.openPage(new PaymentGateway.PageRequest(payment.id(), orderId, payment.amount(),
                properties.returnUrlOf(orderId)));

        // 支払画面の記録
        var used = commandService.recordPage(orderId, opened);
        // 使わない画面（同時のリクエストに先を越された、その間に支払えなくなった）は、支払えないように閉じる
        if (used.filter(page -> page.id().equals(opened.id())).isEmpty()) {
            gateway.closePage(opened.id());
        }
        return used.map(PaymentPage::url);
    }
}
