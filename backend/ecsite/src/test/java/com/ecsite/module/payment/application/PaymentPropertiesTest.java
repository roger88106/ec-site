package com.ecsite.module.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 決済の設定の検証（spec 182 DC-182-1、3）。<br />
 * 支払の期限の上限（30 分）を超えると起動に失敗し、設定の名前が知らされることを確認する。
 */
class PaymentPropertiesTest {

    private static final String RETURN_URL = "ecsite.payment.return-url=http://store/orders/{orderId}";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Payment.class);

    @Test
    @DisplayName("支払の期限が 30 分なら、起動できる")
    void startsWithDeadlineAtLimit() {
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.deadline=30m")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("支払の期限が 31 分なら、起動に失敗し、deadline が知らされる")
    void failsWithDeadlineOverLimit() {
        runner.withPropertyValues(RETURN_URL, "ecsite.payment.deadline=31m")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("deadline");
                });
    }

    /**
     * 決済の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class Payment {
    }
}
