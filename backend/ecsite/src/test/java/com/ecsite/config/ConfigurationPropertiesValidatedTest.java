package com.ecsite.config;

import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 設定の型の検証の確認
 *
 * <p>範囲の検証のテストは {@code TestSettings.bindValidated} で値を当てはめる。この補助は {@code @Validated} を見ずに検証するため、
 * 本番の型から {@code @Validated} を消しても、範囲のテストは通ってしまう。そのため、起動のときに検証が有効になる前提（{@code @Validated} の有無）を
 * 本番のクラスの全件で確認する。
 */
class ConfigurationPropertiesValidatedTest {

    @Test
    @DisplayName("設定の型（@ConfigurationProperties）は全て @Validated を持つ")
    void everyConfigurationPropertiesTypeIsValidated() {
        // 操作：本番のクラスのうち、@ConfigurationProperties の型を集める
        var properties = ProductionClasses.all().stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(ConfigurationProperties.class))
                .toList();

        // 確認：既知の設定の型が集まっていて（空振りでないこと）、@Validated のない型はない
        assertThat(properties.stream().map(JavaClass::getSimpleName).toList())
                .contains("ImgproxyProperties", "OrderProperties", "PaymentProperties", "ReportingProperties");
        assertThat(properties.stream().filter(javaClass -> !javaClass.isAnnotatedWith(Validated.class))
                .map(JavaClass::getName).toList()).isEmpty();
    }
}
