package com.ecsite.module.shipping.infrastructure.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 物流の mock フラグがないときに、起動に失敗させる。<br />
 * 物流サービスの本番の実装はまだないので、フラグがなければ使える実装がない。
 * 追跡の Bean が足りないことで止めると、エラーメッセージがフラグの名前を示さないため、ここで名前を出して止める。
 * 本番の実装を作るときに、この設定を消す。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled", havingValue = false, matchIfMissing = true)
class MockCarrierRequiredConfig {

    /**
     * 起動に失敗させる。
     *
     * @throws IllegalStateException いつも
     */
    MockCarrierRequiredConfig() {
        throw new IllegalStateException(
                "物流サービスの本番の実装がない。mock を使うときは ecsite.shipping.mock.enabled=true を指定する");
    }
}
