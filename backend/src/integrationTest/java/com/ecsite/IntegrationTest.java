package com.ecsite;

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
 * Spring コンテキストを起動し、Testcontainers の PostgreSQL に Flyway のマイグレーションを適用する。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration-test")
public @interface IntegrationTest {
}
