package com.ecsite.module.payment.application;

import org.springframework.context.ApplicationContext;

/**
 * 決済のジョブ（{@link PaymentExpiryJob}）を実行する、統合テストの補助。<br />
 * ジョブのクラスは package-private なので、同じパッケージのこのクラスを通して呼ぶ。
 */
public final class PaymentJobs {

    private PaymentJobs() {
    }

    /**
     * 支払の期限切れのジョブを 1 回実行する。
     *
     * @param context Spring のコンテキスト（テストに注入したもの）
     */
    public static void expire(ApplicationContext context) {
        context.getBean(PaymentExpiryJob.class).expire();
    }
}
