package com.ecsite;

import com.ecsite.config.security.ErrorCodeCheckConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 統合テストの共通設定。<br />
 * Spring コンテキストを起動し、Testcontainers の PostgreSQL に Flyway のマイグレーションを適用する。<br />
 * 認証は、IdP を起動せずにテストの鍵で確認する（{@link TestTokens}）。<br />
 * 非同期の処理を待つ補助（{@link AsyncEvents}）を登録する。<br />
 * 決済の mock は、テスト用の操作を追加した実装で包む（{@link TestPaymentGatewayConfig}）。<br />
 * 物流の mock は、追跡を始めた送り状番号を記録する実装で包む（{@link TestCarrierTrackingConfig}）。<br />
 * MockMvc のエラーの応答のコードが、契約のその操作の一覧か共通のエラーにあることを、毎回確認する（{@link ErrorCodeCheckConfig}）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import({TestcontainersConfig.class, TestSecurityConfig.class, AsyncEvents.class, TestPaymentGatewayConfig.class,
        TestCarrierTrackingConfig.class, ErrorCodeCheckConfig.class})
@ActiveProfiles("integration-test")
public @interface IntegrationTest {
}
