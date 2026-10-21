package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 物流の mock フラグがないときに、起動に失敗させる。<br />
 * 物流サービスの本番の実装はまだないので、フラグがなければ使える実装がない。
 * 追跡の Bean が足りないことで止めると、エラーメッセージがフラグの名前を示さないため、
 * 追跡の Bean の代わりに、作るときにフラグの名前を出して止める Bean を置く（追跡を使う Bean が先に作られても、ここで止まる）。
 * 本番の実装を作るときに、この設定を消す。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled", havingValue = false, matchIfMissing = true)
class MockCarrierRequiredConfig {

    /**
     * 起動に失敗させる。
     *
     * @return 返さない
     * @throws IllegalStateException いつも
     */
    @Bean
    CarrierTracking carrierTrackingRequired() {
        throw new IllegalStateException(
                "物流サービスの本番の実装がない。mock を使うときは ecsite.shipping.mock.enabled=true を指定する");
    }
}
