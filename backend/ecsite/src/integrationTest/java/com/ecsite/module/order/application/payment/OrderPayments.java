package com.ecsite.module.order.application.payment;

import org.springframework.context.ApplicationContext;

import java.util.UUID;

/**
 * 注文の支払の結果（{@link OrderPaymentService}）を反映する、統合テストの補助。<br />
 * サービスのクラスは package-private なので、同じパッケージのこのクラスを通して呼ぶ。
 * ロールバックするテストは決済のイベントが届かないので、リスナーと同じ処理をここで呼ぶ。
 */
public final class OrderPayments {

    private OrderPayments() {
    }

    /**
     * 与信の成功を注文に反映する（支払待ちの注文を受付にする）。
     *
     * @param context Spring のコンテキスト（テストに注入したもの）
     * @param orderId 注文 ID
     */
    public static void accept(ApplicationContext context, UUID orderId) {
        context.getBean(OrderPaymentService.class).accept(orderId);
    }

    /**
     * 支払の期限切れを注文に反映する（支払待ちの注文をキャンセルにする）。
     *
     * @param context Spring のコンテキスト（テストに注入したもの）
     * @param orderId 注文 ID
     */
    public static void expire(ApplicationContext context, UUID orderId) {
        context.getBean(OrderPaymentService.class).expire(orderId);
    }
}
