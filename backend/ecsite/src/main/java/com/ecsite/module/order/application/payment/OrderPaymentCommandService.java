package com.ecsite.module.order.application.payment;

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
 * <p>与信の成功を注文に反映する。注文を行ロックしてから変えるので、何度呼んでも結果は同じ。
 * 支払の期限切れは、キャンセルのサービスが反映する。
 */
@Service
@Transactional
@RequiredArgsConstructor
class OrderPaymentCommandService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 与信の成功を受けて、支払待ちの注文を受付にする。
     *
     * <p>注文がない、または支払待ちでなければ何もしない。
     *
     * @param orderId 注文 ID
     */
    public void accept(@NonNull UUID orderId) {
        var now = clock.instant();
        var found = orderRepository.lockById(orderId);
        if (found.isEmpty()) {
            return;
        }
        var order = found.get();
        if (!order.acceptPayment(now)) {
            return;
        }
        orderRepository.update(order);
    }
}
