package com.ecsite.config;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import com.ecsite.module.shipping.infrastructure.carrier.UnconnectedCarrierTracking;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrierTracking;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 外部サービス（決済、物流）の実装を、mock フラグで選ぶことの検査
 *
 * <p>決済と物流の実装の置き場所だけを読み込み、フラグの有無で、使われる実装と起動の成否を確認する。
 */
class ExternalServiceSelectionTest {

    // ============================== 定数 ==============================

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
     * 起動の失敗の知らせに出ないことを確認する、webhook の秘密の値
     */
    private static final String WEBHOOK_SECRET_VALUE = "whsec_test_secret_value";

    /**
     * webhook の秘密の値だけを渡す設定（秘密の鍵はない）
     */
    private static final String WEBHOOK_SECRET = "ecsite.payment.stripe.webhook-secret=" + WEBHOOK_SECRET_VALUE;

    // ============================== 起動の設定 ==============================

    /**
     * 依存する Bean の代わりの物と、決済の mock の設定だけを持つ基本の設定
     *
     * <p>読み込む実装の置き場所は、テストごとに追加する。
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
            .withBean(DSLContext.class, () -> mock(DSLContext.class))
            .withBean(MessageResolver.class, () -> mock(MessageResolver.class));

    /**
     * 決済と物流の実装の置き場所を読み込む
     */
    private final ApplicationContextRunner runner = base.withUserConfiguration(ExternalServices.class);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("決済の実装の選択")
    class Payment {

        @Test
        @DisplayName("AC-226 決済のフラグを立てると、決済は mock を使う")
        void usesMockPaymentWithFlag() {
            // 操作と確認：両方のフラグを立てて起動すると、決済の実装が mock
            runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(MockPaymentGateway.class);
            });
        }

        @Test
        @DisplayName("AC-226 決済のフラグを立てなければ、決済は Stripe を使う")
        void usesStripeWithoutFlag() {
            // 操作と確認：Stripe の鍵を渡し、決済のフラグを立てずに起動すると、決済の実装が mock でない
            runner.withPropertyValues(SHIPPING_MOCK).withPropertyValues(STRIPE_KEYS).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(PaymentGateway.class)).isNotInstanceOf(MockPaymentGateway.class);
            });
        }

        @Test
        @DisplayName("AC-227 決済のフラグを立てず、Stripe の鍵がなければ、起動に失敗し、足りない設定の名前が知らされる")
        void failsWithoutStripeKeys() {
            // 操作と確認：秘密の鍵を渡さずに起動すると失敗し、原因に足りない設定の名前がある
            runner.withPropertyValues(SHIPPING_MOCK, WEBHOOK_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("secretKey");
            });
        }

        @Test
        @DisplayName("AC-227 Stripe の鍵が足りずに起動に失敗しても、渡した秘密の値は知らされない")
        void doesNotRevealSecretOnFailure() {
            // 操作と確認：webhook の秘密の値だけを渡して起動すると失敗し、原因にその値がない
            runner.withPropertyValues(SHIPPING_MOCK, WEBHOOK_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageNotContaining(WEBHOOK_SECRET_VALUE);
            });
        }

        @Test
        @DisplayName("AC-230 決済を mock にする今までの設定だけでは、mock を使わない")
        void oldGatewaySettingDoesNotSelectMock() {
            // 操作と確認：今までの設定を mock にし、決済のフラグを立てずに起動すると、決済の mock がない
            runner.withPropertyValues(SHIPPING_MOCK, "ecsite.payment.gateway=mock").withPropertyValues(STRIPE_KEYS)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(MockPaymentGateway.class);
                    });
        }
    }

    @Nested
    @DisplayName("物流の実装の選択")
    class Shipping {

        @Test
        @DisplayName("AC-226 物流のフラグを立てると、物流は mock を使う")
        void usesMockCarrierWithFlag() {
            // 操作と確認：両方のフラグを立てて起動すると、物流の実装が mock
            runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CarrierTracking.class)).isInstanceOf(MockCarrierTracking.class);
            });
        }

        @Test
        @DisplayName("AC-226 物流のフラグを立てなければ、物流は接続していない物流サービスを使う")
        void usesUnconnectedCarrierWithoutFlag() {
            // 操作と確認：物流のフラグを書かずに起動すると、物流の実装が接続していない物流サービス
            runner.withPropertyValues(PAYMENT_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CarrierTracking.class)).isInstanceOf(UnconnectedCarrierTracking.class);
            });
        }

        @Test
        @DisplayName("AC-226 物流のフラグを false にすると、物流は接続していない物流サービスを使う")
        void usesUnconnectedCarrierWithFlagFalse() {
            // 操作と確認：物流のフラグを false にして起動すると、物流の実装が接続していない物流サービス
            runner.withPropertyValues(PAYMENT_MOCK, "ecsite.shipping.mock.enabled=false").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CarrierTracking.class)).isInstanceOf(UnconnectedCarrierTracking.class);
            });
        }

        @Test
        @DisplayName("AC-228 物流のフラグを立てなくても、起動に成功する")
        void startsWithoutShippingFlag() {
            // 操作と確認：物流のフラグを書かずに起動しても、失敗しない
            runner.withPropertyValues(PAYMENT_MOCK).run(context -> assertThat(context).hasNotFailed());
        }

        @Test
        @DisplayName("AC-228 物流のフラグを立てなくても、追跡を使う Bean が先に作られて、起動に成功する")
        void startsWithoutShippingFlagEvenIfTrackingIsRequiredFirst() {
            // 操作と確認：追跡を使う Bean を実装の置き場所より先に読み込んでも起動し、その Bean が接続していない物流サービスを受け取る
            base.withUserConfiguration(TrackingUser.class, ExternalServices.class).withPropertyValues(PAYMENT_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(TrackingUser.class).tracking()).isInstanceOf(UnconnectedCarrierTracking.class);
            });
        }
    }

    @Nested
    @DisplayName("mock の画面")
    class MockPages {

        @Test
        @DisplayName("AC-229 決済のフラグを立てると、mock の支払画面がある")
        void mockPaymentPageWithFlag() {
            // 操作と確認：決済のフラグを立てて起動すると、mock の支払画面がある
            runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("mockPaymentPageController");
            });
        }

        @Test
        @DisplayName("AC-229 決済のフラグを立てなければ、mock の支払画面がない")
        void noMockPaymentPageWithoutFlag() {
            // 操作と確認：決済のフラグを立てずに起動すると、mock の支払画面も決済の mock もない
            runner.withPropertyValues(SHIPPING_MOCK).withPropertyValues(STRIPE_KEYS).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("mockPaymentPageController");
                assertThat(context).doesNotHaveBean(MockPaymentGateway.class);
            });
        }

        @Test
        @DisplayName("AC-229 物流のフラグを立てると、mock の配送業者の画面がある")
        void mockCarrierPageWithFlag() {
            // 操作と確認：物流のフラグを立てて起動すると、mock の配送業者の画面がある
            runner.withPropertyValues(PAYMENT_MOCK, SHIPPING_MOCK).run(context ->
                    assertThat(context).hasBean("mockCarrierController"));
        }

        @Test
        @DisplayName("AC-229 物流のフラグを立てなければ、mock の配送業者の画面がない")
        void noMockCarrierPageWithoutFlag() {
            // 操作と確認：物流のフラグを書かずに起動すると、mock の配送業者の画面がない
            runner.withPropertyValues(PAYMENT_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("mockCarrierController");
            });
        }

        @Test
        @DisplayName("AC-229 物流のフラグを立てなければ、mock の配送業者の画面のための、最近の出荷を問い合わせる Bean がない")
        void noMockShipmentQueryWithoutFlag() {
            // 操作と確認：物流のフラグを書かずに起動すると、最近の出荷を問い合わせる Bean がない
            runner.withPropertyValues(PAYMENT_MOCK).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("mockShipmentQuery");
            });
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 決済と物流の実装の置き場所の読み込み
     *
     * <p>接続していない物流サービスは、そのクラスだけを読み込む（同じパッケージの通知のエンドポイントの依存を持ち込まないため）。
     * 統合テストのクラスパスにもこのクラスが入る。統合テストのアプリが読み込まないように、テストの設定として置く
     * （テストのメソッドを入れ子のクラスに置くので、外のクラスがテストのクラスとみなされず、ただの設定では読み込まれる）。
     */
    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan({
            "com.ecsite.module.payment.infrastructure.mock",
            "com.ecsite.module.payment.infrastructure.stripe",
            "com.ecsite.module.shipping.infrastructure.mock"})
    @Import(UnconnectedCarrierTracking.class)
    static class ExternalServices {
    }

    /**
     * 追跡を使う Bean
     *
     * <p>本番では、出荷のアプリケーション層が実装の置き場所より先に作られる。
     *
     * @param tracking 追跡
     */
    record TrackingUser(CarrierTracking tracking) {
    }
}
