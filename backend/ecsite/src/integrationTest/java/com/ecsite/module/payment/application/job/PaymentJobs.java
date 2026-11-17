package com.ecsite.module.payment.application.job;

import com.ecsite.module.payment.application.PaymentQueryService;
import org.springframework.context.ApplicationContext;

import java.util.function.BooleanSupplier;

/**
 * 決済のジョブ（{@link PaymentExpiryJob}）を実行する、統合テストの補助
 *
 * <p>ジョブのクラスは package-private なので、同じパッケージのこのクラスを通して呼ぶ。
 */
public final class PaymentJobs {

    /**
     * インスタンスを作らない。
     */
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

    /**
     * 条件を満たすまで、支払の期限切れのジョブを繰り返し実行する。
     *
     * <p>ジョブは古い順に 1 回 100 件までしか処理しないので、他のテストが残した支払が多いと、1 回ではテストの支払に届かない。
     * テストの支払が処理されたことを条件にして、届くまで実行する。
     *
     * <p>処理できる支払があれば 1 回に少なくとも 1 件は処理するので、回数の上限は、実行の前の期限を過ぎた支払と与信の反映を待つ支払の数に 1 を加えたもの。
     * 上限までに条件を満たさなければ、そのまま戻る（呼ぶ側の確認で失敗する）。
     *
     * @param context Spring のコンテキスト（テストに注入したもの）
     * @param done    テストの支払が処理されたか
     */
    public static void expireUntil(ApplicationContext context, BooleanSupplier done) {
        var queryService = context.getBean(PaymentQueryService.class);
        int maxRuns = queryService.findPastDeadline(Integer.MAX_VALUE).size()
                + queryService.findAwaitingAuthorization(Integer.MAX_VALUE).size() + 1;
        var job = context.getBean(PaymentExpiryJob.class);
        for (int run = 0; run < maxRuns && !done.getAsBoolean(); run++) {
            job.expire();
        }
    }
}
