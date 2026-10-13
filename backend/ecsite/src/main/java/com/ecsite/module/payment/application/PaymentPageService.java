package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 支払の画面のサービス（docs/backend/modules/payment.md「支払（集約）」）。<br />
 * 支払の画面は同時に 1 つだけ開く。決済サービスの呼び出しはトランザクションの外で行う。
 */
@Service
@RequiredArgsConstructor
public class PaymentPageService {

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 決済サービス
     */
    private final PaymentGateway gateway;

    /**
     * 決済の設定
     */
    private final PaymentProperties properties;

    /**
     * 支払の画面の URL を返す。開いている画面があればそれを、なければ決済サービスに作らせる。
     *
     * @param orderId 注文 ID
     * @return 支払の画面の URL。支払がない、支払えない（未払いでない、期限の後）なら空
     */
    public Optional<String> pageUrl(UUID orderId) {
        var payable = commandService.findPayable(orderId);
        if (payable.isEmpty()) {
            return Optional.empty();
        }
        var payment = payable.get();
        if (payment.page().isPresent()) {
            return payment.page().map(PaymentPage::url);
        }
        var opened = gateway.openPage(new PaymentGateway.PageRequest(payment.id(), orderId, payment.amount(),
                properties.returnUrlOf(orderId)));
        var used = commandService.recordPage(orderId, opened);
        // 使わない画面（同時の要求に先を越された、その間に支払えなくなった）は、支払えないように閉じる
        if (used.filter(page -> page.id().equals(opened.id())).isEmpty()) {
            gateway.closePage(opened.id());
        }
        return used.map(PaymentPage::url);
    }
}
