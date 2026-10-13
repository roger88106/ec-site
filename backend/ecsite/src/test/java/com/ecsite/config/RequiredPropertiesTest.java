package com.ecsite.config;

import com.ecsite.config.security.SecurityProperties;
import com.ecsite.module.media.infrastructure.converter.ImgproxyProperties;
import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.storefront.application.ShopProperties;
import org.junit.jupiter.api.DisplayName;
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
 * 環境ごとに変わる設定の値が必須であることの検査（docs/backend/coding.md 第 6.1 節、spec 005 DC-42）。<br />
 * 設定の値の型ごとに、必須の値を 1 つ渡さないと起動に失敗し、その値の名前が知らされることを確かめる。
 */
class RequiredPropertiesTest {

    /**
     * 設定の値の型を有効にする設定、渡す値（必須の 1 つを除く）、除いた値の名前を返す。
     *
     * @return 見本
     */
    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(Security.class, new String[]{
                        "ecsite.security.admin.issuer-uri=http://idp/admin", "ecsite.security.admin.audience=admin",
                        "ecsite.security.store.issuer-uri=http://idp/store"}, "audience"),
                Arguments.of(Imgproxy.class, new String[]{
                        "ecsite.media.converter.base-url=http://imgproxy", "ecsite.media.converter.key=aa"}, "salt"),
                Arguments.of(MediaStorage.class, new String[]{
                        "ecsite.media.storage.region=ap-northeast-1", "ecsite.media.storage.incoming-bucket=in",
                        "ecsite.media.storage.public-bucket=out"}, "publicBaseUrl"),
                Arguments.of(Order.class, new String[]{}, "shippingTaxCategoryId"),
                Arguments.of(Shop.class, new String[]{}, "contactEmail"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("必須の値がなければ、起動に失敗し、足りない値の名前が知らされる")
    void failsWithoutRequiredValue(Class<?> configuration, String[] values, String missing) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(configuration)
                .withPropertyValues(values)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(missing);
                });
    }

    /**
     * 認証の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Security {
    }

    /**
     * 画像の変換の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ImgproxyProperties.class)
    static class Imgproxy {
    }

    /**
     * 画像の置き場所の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MediaStorageProperties.class)
    static class MediaStorage {
    }

    /**
     * 注文の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrderProperties.class)
    static class Order {
    }

    /**
     * 店舗の情報の設定を有効にする
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ShopProperties.class)
    static class Shop {
    }
}
