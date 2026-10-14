package com.ecsite.module.order.application.payment;

import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 注文の支払の結果のサービス（docs/backend/modules/order.md「支払」）。<br />
 * 決済のイベント（与信の成功、支払の期限切れ）を注文に反映する。注文を行ロックしてから変えるので、何度呼んでも結果は同じ。
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrderPaymentService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 注文手続きリポジトリ（注文のカートを知るため）
     */
    private final CheckoutRepository checkoutRepository;

    /**
     * カートの腐敗防止層
     */
    private final CartDirectory cartDirectory;

    /**
     * キャンセルのサービス
     */
    private final OrderCancellationService cancellationService;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 与信の成功：支払待ちの注文を受付にし、注文した品目をカートから除く。支払待ちでなければ何もしない。
     *
     * @param orderId 注文 ID
     */
    public void accept(UUID orderId) {
        orderRepository.lockById(orderId).ifPresent(order -> {
            if (!order.acceptPayment(clock.instant())) {
                return;
            }
            orderRepository.update(order);
            var variantIds = order.lines().stream().map(line -> line.variantId()).distinct().toList();
            checkoutRepository.findById(order.checkoutId())
                    .ifPresent(checkout -> cartDirectory.removeItems(checkout.cartId(), variantIds));
        });
    }

    /**
     * 支払の期限切れ：支払待ちの注文をキャンセル（支払期限切れ）にし、在庫を戻す。支払待ちでなければ何もしない。
     *
     * @param orderId 注文 ID
     */
    public void expire(UUID orderId) {
        cancellationService.expirePayment(orderId);
    }
}
