package com.ecsite.module.order.application.payment;

import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 注文の支払の結果のサービス
 *
 * <p>与信の成功を注文に反映する。注文を行ロックしてから変えるので、何度呼んでも結果は同じ。
 * 支払の期限切れは、キャンセルのサービスが反映する。
 */
@Service
@Transactional
@RequiredArgsConstructor
class OrderPaymentService {

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
     * 時計
     */
    private final Clock clock;

    /**
     * 与信の成功を受けて、支払待ちの注文を受付にし、注文した品目をカートから除く。
     *
     * <p>注文がない、または支払待ちでなければ何もしない。
     *
     * @param orderId 注文 ID
     */
    public void accept(@NonNull UUID orderId) {
        // 注文の受付
        var now = clock.instant();
        var accepted = orderRepository.lockById(orderId).filter(order -> order.acceptPayment(now));
        if (accepted.isEmpty()) {
            return;
        }
        var order = accepted.get();
        orderRepository.update(order);

        // 注文した品目をカートから除く
        removeFromCart(order);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文した品目を、注文手続きのカートから除く。
     *
     * <p>注文手続きが消えていれば何もしない。
     */
    private void removeFromCart(Order order) {
        var variantIds = order.lines().stream().map(OrderLine::variantId).distinct().toList();
        checkoutRepository.findById(order.checkoutId())
                .ifPresent(checkout -> cartDirectory.removeItems(checkout.cartId(), variantIds));
    }
}
