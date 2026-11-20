package com.ecsite.module.order.application.payment;

import com.ecsite.module.order.application.OrderCancellationCommandService;
import com.ecsite.module.order.application.OrderCartCommandService;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.support.async.AsyncEvents;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * 注文の支払の結果を反映する、統合テストの補助
 *
 * <p>{@link OrderPaymentCommandService} と {@link OrderCancellationCommandService} を呼ぶ。
 * サービスのクラスは package-private なので、同じパッケージのこのクラスを通して呼ぶ。
 * 受付にする処理と、注文した品目をカートから除く処理（{@link OrderCartCommandService}）は、別のリスナーが動かす。
 * ロールバックするテストはイベントが届かないので、リスナーと同じ処理をここで呼ぶ（カートから除く処理は同期で呼ぶ）。
 */
public final class OrderPayments {

    /**
     * インスタンスを作らない。
     */
    private OrderPayments() {
    }

    /**
     * 与信の成功を注文に反映する。
     *
     * <p>支払待ちの注文を受付にし、注文した品目をカートから除く。与信の成功のリスナーと同じ処理なので、すでに受付なら何も変えない。
     *
     * <p>テストのトランザクションの中（ロールバックするテスト）では、イベントが届かないので、支払待ちから受付への遷移のリスナーと
     * 同じ処理（カートから除く）もここで呼ぶ。トランザクションの外（コミットするテスト）では、イベントが届くので、
     * 受付になって、そのイベントの処理が終わるのを待つ（待たないと、続けて変えるカートと、除く処理が競合する）。
     *
     * @param context テストに注入した Spring のコンテキスト
     * @param orderId 注文 ID
     */
    public static void accept(ApplicationContext context, UUID orderId) {
        var orders = context.getBean(OrderRepository.class);
        var wasPending = orders.findById(orderId).filter(order -> order.status() == OrderStatus.PENDING_PAYMENT).isPresent();
        context.getBean(OrderPaymentCommandService.class).accept(orderId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (wasPending) {
                context.getBean(OrderCartCommandService.class).removeOrderedItems(orderId);
            }
            return;
        }
        // 受付のコミットまで待つ（与信の成功のリスナーが先に受付にしていることがある）
        await().atMost(AsyncEvents.TIMEOUT).until(() -> orders.findById(orderId)
                .filter(order -> order.status() == OrderStatus.PENDING_PAYMENT).isEmpty());
        context.getBean(AsyncEvents.class).awaitHandled(event ->
                event instanceof OrderStatusChanged changed && changed.orderId().equals(orderId));
    }

    /**
     * 支払の期限切れを注文に反映する。
     *
     * <p>支払待ちの注文を、支払期限切れのキャンセルにする。
     *
     * @param context テストに注入した Spring のコンテキスト
     * @param orderId 注文 ID
     */
    public static void expire(ApplicationContext context, UUID orderId) {
        context.getBean(OrderCancellationCommandService.class).expirePayment(orderId);
    }
}
