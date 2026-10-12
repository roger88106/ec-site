package com.ecsite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Map;

/**
 * API のパスのプレフィックス（constitution 4.3）。<br />
 * OpenAPI から生成したインターフェースのパスは {@code servers.url} を含まないので、
 * 実装する Controller に、生成元の契約に応じたプレフィックスを付ける。
 * <ul>
 *     <li>{@code admin.yaml}（{@code com.ecsite.api.admin}）：{@code /api/admin}</li>
 *     <li>{@code store.yaml}（{@code com.ecsite.api.store}）：{@code /api/store}</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class ApiPathConfig implements WebMvcConfigurer {

    /**
     * 生成したインターフェースのパッケージ → プレフィックス
     */
    private static final Map<String, String> PREFIXES = Map.of(
            "com.ecsite.api.admin", "/api/admin",
            "com.ecsite.api.store", "/api/store");

    /**
     * 生成したインターフェースのパッケージごとに、パスの前に付ける部分を決める。
     *
     * @param configurer パスの対応の設定
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PREFIXES.forEach((apiPackage, prefix) ->
                configurer.addPathPrefix(prefix, controller -> implementsApiOf(controller, apiPackage)));
    }

    /**
     * Controller が、指定したパッケージの生成インターフェースを実装しているか。
     *
     * @param controller Controller のクラス
     * @param apiPackage 生成インターフェースのパッケージ
     * @return 実装していれば true
     */
    private static boolean implementsApiOf(Class<?> controller, String apiPackage) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(controller))
                .anyMatch(api -> api.getPackageName().equals(apiPackage));
    }
}
