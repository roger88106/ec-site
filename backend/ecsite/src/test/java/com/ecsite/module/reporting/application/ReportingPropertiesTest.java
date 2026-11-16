package com.ecsite.module.reporting.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの設定の検証
 *
 * <p>会計年度の始まりの月は 1〜12 で、範囲の外では起動に失敗し、設定の名前が知らされることを確認する。
 */
class ReportingPropertiesTest {

    // ============================== 対象 ==============================

    /**
     * レポートの設定だけを読む Spring のコンテキスト
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Reporting.class);

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(ints = {1, 12})
    @DisplayName("会計年度の始まりの月が範囲の端なら、起動でき、その月になる")
    void startsWithMonthAtBounds(int month) {
        // 操作と確認：起動でき、設定した月を持つ
        runner.withPropertyValues("ecsite.reporting.fiscal-year-start-month=" + month)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ReportingProperties.class).fiscalYearStartMonth()).isEqualTo(month);
                });
    }

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(ints = {0, 13})
    @DisplayName("会計年度の始まりの月が範囲の外なら、起動に失敗し、fiscalYearStartMonth が知らされる")
    void failsWithMonthOutOfBounds(int month) {
        // 操作と確認：起動に失敗し、原因に設定の名前がある
        runner.withPropertyValues("ecsite.reporting.fiscal-year-start-month=" + month)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("fiscalYearStartMonth");
                });
    }

    // ============================== 準備の補助 ==============================

    /**
     * レポートの設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReportingProperties.class)
    static class Reporting {
    }
}
