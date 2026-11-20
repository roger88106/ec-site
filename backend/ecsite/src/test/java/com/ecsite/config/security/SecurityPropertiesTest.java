package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 認証の設定の検証
 *
 * <p>運営者と消費者の realm の issuer が同じなら起動に失敗し、2 つの設定の名前が知らされることを確認する。
 */
class SecurityPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 運営者と消費者の realm に共通の audience
     *
     * <p>issuer が違えば、audience が同じでも起動できることを確認するために、両方の realm に使う。
     */
    private static final String AUDIENCE = "ec-api";

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-2 運営者と消費者の realm の issuer が同じなら、起動に失敗し、2 つの設定の名前が知らされる")
    void failsWithSameIssuer() {
        // 操作と確認：同じ issuer では起動に失敗し、原因に 2 つの設定の名前がある
        runner.withPropertyValues(
                        "ecsite.security.admin.issuer-uri=http://idp.test/realms/ec", "ecsite.security.admin.audience=ec-admin-api",
                        "ecsite.security.store.issuer-uri=http://idp.test/realms/ec", "ecsite.security.store.audience=ec-store-api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("admin.issuer-uri").hasMessageContaining("store.issuer-uri");
                });
    }

    @Test
    @DisplayName("DC-477-2 運営者と消費者の realm の issuer が違えば、audience が同じでも起動できる")
    void startsWithDifferentIssuers() {
        // 操作と確認：issuer が違い audience が同じ設定で起動できる
        runner.withPropertyValues(
                        "ecsite.security.admin.issuer-uri=http://idp.test/realms/ec-admin", "ecsite.security.admin.audience=" + AUDIENCE,
                        "ecsite.security.store.issuer-uri=http://idp.test/realms/ec-store", "ecsite.security.store.audience=" + AUDIENCE)
                .run(context -> assertThat(context).hasNotFailed());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 認証の設定だけを読む Spring のコンテキスト
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Security.class);

    /**
     * 認証の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Security {
    }
}
