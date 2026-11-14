package com.ecsite;

import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCaches;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 統合テストのコンテキストで共有する、変えられる部品を、毎回のテストの前に最初の状態に戻す拡張
 *
 * <p>{@link IntegrationTest} が登録する。同じコンテキストのテストは部品を共有するので、戻さないと前のテストの状態が残る。
 * 各テストの {@code @BeforeEach} より前に動くので、テストは {@code @BeforeEach} で必要な状態を決めてよい。
 * 戻すものは次のとおり。
 * <ul>
 *   <li>時計：{@link TestClockConfig#INITIAL_INSTANT} に戻す</li>
 *   <li>決済の mock（{@link TestPaymentGateway}）：テストだけで使う状態を空にする。決済の mock を使わない設定では、ないので戻さない</li>
 *   <li>税率のキャッシュ：捨てる（次の取得で税モジュールから取得し直す）</li>
 * </ul>
 */
public final class SharedStateReset implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        var applicationContext = SpringExtension.getApplicationContext(context);
        applicationContext.getBean(MutableClock.class).setInstant(TestClockConfig.INITIAL_INSTANT);
        applicationContext.getBeanProvider(TestPaymentGateway.class).ifAvailable(TestPaymentGateway::reset);
        TaxRateScheduleCaches.evict(applicationContext);
    }
}
