package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 運用ポートの起動時の検査の単体テスト
 *
 * <p>認証の設定と運用ポートの検査だけを読み込み、運用ポートと業務ポートの設定で、起動の成否を確認する。
 */
class ManagementPortCheckTest {

    // ============================== 定数 ==============================

    /**
     * 運用のパスを業務ポートで公開することを許す設定の名前
     */
    private static final String ALLOW = "ecsite.security.allow-management-on-business-port";

    // ============================== テスト ==============================

    /**
     * 運用のパスが業務ポートで公開される設定の、ケース名と設定の値を返す。
     *
     * @return テストの入力（ケース名と設定の値）
     */
    static Stream<Arguments> sameCases() {
        return Stream.of(
                Arguments.of("運用ポートと業務ポートが同じ", new String[]{"management.server.port=8080", "server.port=8080"}),
                Arguments.of("運用ポートを設定しない", new String[]{"server.port=8080"}),
                Arguments.of("運用ポートが業務ポートを参照する", new String[]{"management.server.port=${server.port}", "server.port=8090"}),
                Arguments.of("業務ポートを設定せず、運用ポートが業務ポートの既定と同じ", new String[]{"management.server.port=8080"}));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("sameCases")
    @DisplayName("DC-477-1 運用のパスが業務ポートで公開される設定なら、起動に失敗し、ポートを分けるか、許す設定にすることが、メッセージで分かる")
    void failsWhenManagementOnBusinessPort(String name, String[] ports) {
        // 操作と確認：起動に失敗し、原因に運用ポートの設定と、許す設定の名前がある
        runner.withPropertyValues(ports)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("management.server.port")
                            .hasMessageContaining(ALLOW);
                });
    }

    @Test
    @DisplayName("DC-477-1 運用ポートが業務ポートと違えば、起動できる")
    void startsWhenPortsDiffer() {
        // 操作と確認：9080 と 8080 で起動できる
        runner.withPropertyValues("management.server.port=9080", "server.port=8080")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("DC-477-1 運用ポートを無効にすれば、起動できる")
    void startsWhenManagementPortDisabled() {
        // 操作と確認：運用ポートを -1 にすると起動できる
        runner.withPropertyValues("management.server.port=-1", "server.port=8080")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("DC-477-1 運用のパスを業務ポートで公開することを設定で許せば、同じポートでも起動できる")
    void startsWhenAllowed() {
        // 操作と確認：同じポートで、許す設定を立てると起動できる
        runner.withPropertyValues("management.server.port=8080", "server.port=8080", ALLOW + "=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 認証の設定と運用ポートの検査だけを読む Spring のコンテキスト
     *
     * <p>認証の設定の必須の値には、起動できる値を渡す。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Security.class)
            .withBean(ManagementPortCheck.class)
            .withPropertyValues(
                    "ecsite.security.admin.issuer-uri=http://idp.test/realms/ec-admin", "ecsite.security.admin.audience=ec-admin-api",
                    "ecsite.security.store.issuer-uri=http://idp.test/realms/ec-store", "ecsite.security.store.audience=ec-store-api");

    /**
     * 認証の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Security {
    }
}
