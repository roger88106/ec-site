package com.ecsite.module.order.application.payment;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.order.application.OrderCancellationService;
import com.ecsite.module.order.application.port.CartDirectory;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 注文の支払の結果のサービス
 *
 * <p>与信の成功と支払の期限切れを注文に反映する。注文を行ロックしてから変えるので、何度呼んでも結果は同じ。
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
     * キャンセルのサービス
     */
    private final OrderCancellationService cancellationService;

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
        var found = orderRepository.lockById(orderId);
        if (found.isEmpty()) {
            return;
        }
        var order = found.get();
        if (!order.acceptPayment(clock.instant())) {
            return;
        }
        orderRepository.update(order);

        // 注文した品目をカートから除く
        var variantIds = order.lines().stream().map(line -> line.variantId()).distinct().toList();
        var checkout = checkoutRepository.findById(order.checkoutId());
        if (checkout.isPresent()) {
            cartDirectory.removeItems(checkout.get().cartId(), variantIds);
        }
    }

    /**
     * 支払の期限切れを受けて、支払待ちの注文をキャンセル（支払期限切れ）にし、在庫を戻す。
     *
     * <p>支払待ちでなければ何もしない。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 注文がない（{@code NOT_FOUND}）
     */
    public void expire(@NonNull UUID orderId) {
        cancellationService.expirePayment(orderId);
    }
}
