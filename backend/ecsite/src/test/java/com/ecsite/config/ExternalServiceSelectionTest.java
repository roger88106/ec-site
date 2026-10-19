package com.ecsite.config;

import com.ecsite.common.message.MessageHelper;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrierTracking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 外部サービス（決済、物流）の実装を、mock フラグで選ぶことの検査。<br />
 * 決済と物流の実装の置き場所だけを読み込み、フラグの有無で、使われる実装と起動の成否を確かめる。
 */
class ExternalServiceSelectionTest {

    /**
     * 物流の mock を使う設定
     */
    private static final String SHIPPING_MOCK = "ecsite.shipping.mock.enabled=true";

    /**
     * 決済の mock を使う設定
     */
    private static final String PAYMENT_MOCK = "ecsite.payment.mock.enabled=true";

    /**
     * Stripe の鍵（テスト用の値）
     */
    private static final String[] STRIPE_KEYS = {
            "ecsite.payment.stripe.secret-key=sk_test_dummy", "ecsite.payment.stripe.webhook-secret=whsec_test_dummy"};

    /**
     * 依存する Bean の代わりの物と、決済の mock の設定だけを持つ。読み込む実装の置き場所は、テストごとに足す
     */
    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withPropertyValues("ecsite.payment.mock.base-url=http://localhost:8080")
            .withBean(PaymentNotificationReceiver.class, () -> mock(PaymentNotificationReceiver.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(CarrierSignature.class, () -> new CarrierSignature("test-carrier-webhook-secret"))
            .withBean(JsonMapper.class, JsonMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ShipmentRepository.class, () -> mock(ShipmentRepository.class))
            .withBean(MessageHelper.class, () -> mock(MessageHelper.class));

    /**
     * 決済と物流の実装の置き場所を読み込む
     */
    private final ApplicationContextRunner runner = base.withUserConfiguration(ExternalServices.class);

    @Test
    @DisplayName("AC-226 決済のフラグを立てると、決済は mock を使う")
    void usesMockPaymentWithFlag() {
        runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(MockPaymentGateway.class);
        });
    }

    @Test
    @DisplayName("AC-226 決済のフラグを立てなければ、決済は Stripe を使う")
    void usesStripeWithoutFlag() {
        runner.withPropertyValues(SHIPPING_MOCK).withPropertyValues(STRIPE_KEYS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PaymentGateway.class)).isNotInstanceOf(MockPaymentGateway.class);
        });
    }

    @Test
    @DisplayName("AC-226 物流のフラグを立てると、物流は mock を使う")
    void usesMockCarrierWithFlag() {
        runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CarrierTracking.class)).isInstanceOf(MockCarrierTracking.class);
        });
    }

    @Test
    @DisplayName("AC-227 決済のフラグを立てず、Stripe の鍵がなければ、起動に失敗し、足りない設定の名前が知らされ、秘密情報は出ない")
    void failsWithoutStripeKeys() {
        runner.withPropertyValues(SHIPPING_MOCK, "ecsite.payment.stripe.webhook-secret=whsec_test_secret_value").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("secretKey")
                    .hasMessageNotContaining("whsec_test_secret_value");
        });
    }

    @Test
    @DisplayName("AC-228 物流のフラグを立てなければ、起動に失敗し、フラグの名前が知らされる")
    void failsWithoutShippingFlag() {
        runner.withPropertyValues(PAYMENT_MOCK).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("ecsite.shipping.mock.enabled");
        });
    }

    @Test
    @DisplayName("AC-228 物流のフラグを立てなければ、追跡を使う Bean が先に作られても、フラグの名前が知らされる")
    void failsWithFlagNameEvenIfTrackingIsRequiredFirst() {
        base.withUserConfiguration(TrackingUser.class, ExternalServices.class).withPropertyValues(PAYMENT_MOCK).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("ecsite.shipping.mock.enabled");
        });
    }

    @Test
    @DisplayName("AC-229 決済のフラグを立てなければ、mock の支払の画面がない")
    void noMockPaymentPageWithoutFlag() {
        runner.withPropertyValues(SHIPPING_MOCK).withPropertyValues(STRIPE_KEYS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("mockPaymentPageController");
            assertThat(context).doesNotHaveBean(MockPaymentGateway.class);
        });
    }

    @Test
    @DisplayName("AC-229 物流のフラグを立てたときだけ、mock の配送業者の画面がある")
    void mockCarrierPageOnlyWithFlag() {
        runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context ->
                assertThat(context).hasBean("mockCarrierController"));
    }

    @Test
    @DisplayName("AC-230 決済を mock にする今までの設定だけでは、mock を使わない")
    void oldGatewaySettingDoesNotSelectMock() {
        runner.withPropertyValues(SHIPPING_MOCK, "ecsite.payment.gateway=mock").withPropertyValues(STRIPE_KEYS)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MockPaymentGateway.class);
                });
    }

    /**
     * 決済と物流の実装の置き場所を読み込む
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan({
            "com.ecsite.module.payment.infrastructure.mock",
            "com.ecsite.module.payment.infrastructure.stripe",
            "com.ecsite.module.shipping.infrastructure.mock"})
    static class ExternalServices {
    }

    /**
     * 追跡を使う Bean（本番では、出荷のアプリケーション層が実装の置き場所より先に作られる）
     *
     * @param tracking 追跡
     */
    record TrackingUser(CarrierTracking tracking) {
    }
}
