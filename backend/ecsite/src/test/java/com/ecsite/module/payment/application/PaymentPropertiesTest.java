package com.ecsite.module.payment.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 決済の設定の検証
 *
 * <p>支払の期限の上限（30 分）を超えると起動に失敗し、設定の名前が知らされることを確認する。
 * 与信の期限が近いとする期間が与信の有効期間より短くないと、起動に失敗し、2 つの設定の名前が知らされることも確認する。
 */
class PaymentPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 必須の戻る先の URL
     */
    private static final String RETURN_URL = "ecsite.payment.return-url=http://store/orders/{orderId}";

    // ============================== 対象 ==============================

    /**
     * 決済の設定だけを読む Spring のコンテキスト
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Payment.class);

    // ============================== テスト ==============================

    @Test
    @DisplayName("支払の期限が上限の 30 分なら、起動できる")
    void startsWithDeadlineAtLimit() {
        // 操作と確認：30 分で起動できる
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.deadline=30m")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("支払の期限が上限を超えると、起動に失敗し、deadline が知らされる")
    void failsWithDeadlineOverLimit() {
        // 操作と確認：31 分では起動に失敗し、原因に設定の名前がある
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.deadline=31m")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("deadline");
                });
    }

    @Test
    @DisplayName("与信の期限が近いとする期間が、与信の有効期間より短ければ、起動できる")
    void startsWithAuthorizationWarningShorterThanValidity() {
        // 操作と確認：有効期間 2 日に対して、2 日の 1ms 前で起動できる
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.authorization-validity=2d",
                        "ecsite.payment.authorization-warning=172799999ms")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("与信の期限が近いとする期間が、与信の有効期間と同じなら、起動に失敗し、2 つの設定の名前が知らされる")
    void failsWithAuthorizationWarningNotShorterThanValidity() {
        // 操作と確認：有効期間と同じ 2 日では起動に失敗し、原因に 2 つの設定の名前がある
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.authorization-validity=2d",
                        "ecsite.payment.authorization-warning=2d")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("authorization-warning").hasMessageContaining("authorization-validity");
                });
    }

    @ParameterizedTest(name = "「{0}」が {1}")
    @CsvSource({
            "deadline, 0s", "deadline, -1s", "deadline, 999us",
            "expiry-interval, 0s", "expiry-interval, -1s", "expiry-interval, 999us",
            "awaiting-authorization-warning, 0s", "awaiting-authorization-warning, -1s", "awaiting-authorization-warning, 999us",
            "authorization-validity, 0s", "authorization-validity, -1s", "authorization-validity, 999us",
            "capture-reconcile-delay, 0s", "capture-reconcile-delay, -1s", "capture-reconcile-delay, 999us"})
    @DisplayName("下限が 1ms の時間の設定が、0、負の値、1ms 未満（999us）なら、値の検証で拒否される")
    void rejectsDurationBelowMinimum(String name, String value) {
        // 準備：必須の値と、許す範囲の外の値
        var values = new HashMap<>(Map.of("return-url", "http://store/orders/{orderId}"));
        values.put(name, value);

        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.payment", PaymentProperties.class, values))
                .isInstanceOf(BindException.class)
                .hasCauseInstanceOf(BindValidationException.class);
    }

    @ParameterizedTest(name = "「{0}」")
    @CsvSource({"deadline", "expiry-interval", "awaiting-authorization-warning", "authorization-validity",
            "capture-reconcile-delay"})
    @DisplayName("下限が 1ms の時間の設定が、下限の 1ms なら、値の検証を通り、その値になる")
    void acceptsDurationAtMinimum(String name) {
        // 操作：必須の値と 1ms を検証付きで当てはめる。与信の有効期間が期限が近いとする期間より長くなるよう、後者を 0 にする
        var values = new HashMap<>(Map.of("return-url", "http://store/orders/{orderId}", "authorization-warning", "0s"));
        values.put(name, "1ms");
        var properties = TestSettings.bindValidated("ecsite.payment", PaymentProperties.class, values);

        // 確認：1ms になる
        var actual = switch (name) {
            case "deadline" -> properties.deadline();
            case "expiry-interval" -> properties.expiryInterval();
            case "awaiting-authorization-warning" -> properties.awaitingAuthorizationWarning();
            case "capture-reconcile-delay" -> properties.captureReconcileDelay();
            default -> properties.authorizationValidity();
        };
        assertThat(actual).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    @DisplayName("与信の期限が近いとする期間が負なら、値の検証で拒否される")
    void rejectsNegativeAuthorizationWarning() {
        // 操作と確認：-1s は値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.payment", PaymentProperties.class,
                Map.of("return-url", "http://store/orders/{orderId}", "authorization-warning", "-1s")))
                .isInstanceOf(BindException.class)
                .hasCauseInstanceOf(BindValidationException.class);
    }

    @Test
    @DisplayName("与信の期限が近いとする期間が 0 なら、値の検証を通る")
    void acceptsZeroAuthorizationWarning() {
        // 操作：0s を検証付きで当てはめる
        var properties = TestSettings.bindValidated("ecsite.payment", PaymentProperties.class,
                Map.of("return-url", "http://store/orders/{orderId}", "authorization-warning", "0s"));

        // 確認：0 になる
        assertThat(properties.authorizationWarning()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("売上確定の照合を始めるまでの時間は、既定で 10 分")
    void captureReconcileDelayDefault() {
        // 操作：必須の値だけを検証付きで当てはめる
        var properties = TestSettings.bindValidated("ecsite.payment", PaymentProperties.class,
                Map.of("return-url", "http://store/orders/{orderId}"));

        // 確認：10 分
        assertThat(properties.captureReconcileDelay()).isEqualTo(Duration.ofMinutes(10));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 決済の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class Payment {
    }
}
