package com.ecsite.module.order.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.application.port.PaymentDirectory;
import com.ecsite.module.order.application.port.StockDirectory;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * キャンセルのサービス（docs/backend/modules/order.md「状態遷移」）。<br />
 * 注文を行ロックしてから状態を変え、キャンセルになったら同じトランザクションで在庫を戻し、支払を取り消させる（与信の取消は決済がコミットの後に行う）。
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    /**
     * 注文リポジトリ
     */
    private final OrderRepository orderRepository;

    /**
     * 在庫の腐敗防止層
     */
    private final StockDirectory stockDirectory;

    /**
     * 決済の腐敗防止層
     */
    private final PaymentDirectory paymentDirectory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 会員がキャンセルを申請する。注文した会員でなければ、ないときと同じく 404。
     *
     * @param orderId  注文 ID
     * @param memberId 要求した会員の ID
     * @param reason   理由（任意）
     */
    @Transactional
    public void request(UUID orderId, UUID memberId, String reason) {
        var order = lock(orderId);
        if (!order.memberId().equals(memberId)) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }
        order.requestCancellation(reason, clock.instant());
        orderRepository.update(order);
    }

    /**
     * キャンセルの申請を承認する。キャンセルになったら在庫を戻す。
     *
     * @param orderId  注文 ID
     * @param operator 承認した運営者
     */
    @Transactional
    public void approve(UUID orderId, Operator operator) {
        changeAndRestore(orderId, order -> order.approveCancellation(operator, clock.instant()));
    }

    /**
     * キャンセルの申請を却下する。
     *
     * @param orderId  注文 ID
     * @param operator 却下した運営者
     */
    @Transactional
    public void reject(UUID orderId, Operator operator) {
        var order = lock(orderId);
        order.rejectCancellation(operator, clock.instant());
        orderRepository.update(order);
    }

    /**
     * 運営者の判断でキャンセルする。キャンセルになったら在庫を戻す。
     *
     * @param orderId  注文 ID
     * @param reason   理由（必須）
     * @param operator キャンセルした運営者
     */
    @Transactional
    public void cancel(UUID orderId, String reason, Operator operator) {
        changeAndRestore(orderId, order -> order.cancelByOperator(reason, operator, clock.instant()));
    }

    /**
     * 支払の期限切れでキャンセルにする。支払待ちでなければ何もしない。キャンセルになったら在庫を戻す。
     *
     * @param orderId 注文 ID
     */
    @Transactional
    public void expirePayment(UUID orderId) {
        changeAndRestore(orderId, order -> order.expirePayment(clock.instant()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 注文を行ロックして取得する。なければ見つからない。
     */
    private Order lock(UUID orderId) {
        return orderRepository.lockById(orderId).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 注文をキャンセルにする操作を行い、キャンセルになったら保存して明細の数量だけ在庫を戻し、支払を取り消させる。<br />
     * すでにキャンセルなら何もしない。在庫の戻しに失敗すれば、注文の変更ごと取り消される。
     *
     * @param orderId 注文 ID
     * @param cancel  キャンセルにする操作。キャンセルにしたら true
     */
    private void changeAndRestore(UUID orderId, Predicate<Order> cancel) {
        var order = lock(orderId);
        if (!cancel.test(order)) {
            return;
        }
        orderRepository.update(order);
        stockDirectory.restore(quantities(order), order.id(), order.orderNumber().value());
        paymentDirectory.cancel(order.id());
    }

    /**
     * 明細を、バリアント ID → 数量にする。
     */
    private static Map<UUID, Integer> quantities(Order order) {
        var quantities = new LinkedHashMap<UUID, Integer>();
        order.lines().forEach(line -> quantities.merge(line.variantId(), line.quantity(), Integer::sum));
        return quantities;
    }
}
