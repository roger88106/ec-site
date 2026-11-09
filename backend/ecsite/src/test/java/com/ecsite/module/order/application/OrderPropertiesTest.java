package com.ecsite.module.order.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の設定の単体テスト
 */
class OrderPropertiesTest {

    private static final String TAX_CATEGORY_ID = "ecsite.order.shipping-tax-category-id=00000000-0000-0000-0000-000000000001";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Enabled.class);

    @Test
    @DisplayName("NFR-411-2 支払待ちの注文の上限は、設定がなければ 3 件である")
    void defaultsPendingOrderLimitToThree() {
        runner.withPropertyValues(TAX_CATEGORY_ID).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrderProperties.class).pendingOrderLimit()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("NFR-411-2 支払待ちの注文の上限は、設定で変えられる")
    void overridesPendingOrderLimit() {
        runner.withPropertyValues(TAX_CATEGORY_ID, "ecsite.order.pending-order-limit=5").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OrderProperties.class).pendingOrderLimit()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("NFR-411-2 支払待ちの注文の上限が 1 未満なら、起動に失敗する")
    void rejectsLimitBelowOne() {
        runner.withPropertyValues(TAX_CATEGORY_ID, "ecsite.order.pending-order-limit=0").run(context ->
                assertThat(context).hasFailed());
    }

    /**
     * 注文の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrderProperties.class)
    static class Enabled {
    }
}
