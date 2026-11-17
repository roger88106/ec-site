package com.ecsite.module.payment.application;

import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
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

    @ParameterizedTest(name = "「{0}」が {1}")
    @CsvSource({
            "deadline, 0s", "deadline, -1s",
            "expiry-interval, 0s", "expiry-interval, -1s",
            "awaiting-authorization-warning, 0s", "awaiting-authorization-warning, -1s",
            "authorization-validity, 0s", "authorization-validity, -1s"})
    @DisplayName("正の値だけの時間の設定が 0 以下なら、値の検証で拒否される")
    void rejectsNonPositiveDuration(String name, String value) {
        // 準備：必須の値と、下限の外の値
        var values = new HashMap<>(Map.of("return-url", "http://store/orders/{orderId}"));
        values.put(name, value);

        // 操作と確認：値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.payment", PaymentProperties.class, values))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("承認期限の警告の時間が負なら、値の検証で拒否される")
    void rejectsNegativeAuthorizationWarning() {
        // 操作と確認：-1s は値の検証で拒否される
        assertThatThrownBy(() -> TestSettings.bindValidated("ecsite.payment", PaymentProperties.class,
                Map.of("return-url", "http://store/orders/{orderId}", "authorization-warning", "-1s")))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("承認期限の警告の時間が 0 なら、値の検証を通る")
    void acceptsZeroAuthorizationWarning() {
        // 操作：0s を検証付きで当てはめる
        var properties = TestSettings.bindValidated("ecsite.payment", PaymentProperties.class,
                Map.of("return-url", "http://store/orders/{orderId}", "authorization-warning", "0s"));

        // 確認：0 になる
        assertThat(properties.authorizationWarning()).isEqualTo(Duration.ZERO);
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
