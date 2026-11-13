package com.ecsite.config;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * Spring MVC の設定
 *
 * <p>OpenAPI から生成したインターフェースと列挙型を、Spring MVC で使えるようにする。
 */
@Configuration(proxyBeanMethods = false)
class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 生成したインターフェースのパッケージ → パスの前に付ける部分
     */
    private static final Map<String, String> PREFIXES = Map.of(
            "com.ecsite.api.admin", "/api/admin",
            "com.ecsite.api.store", "/api/store");

    /**
     * 生成したインターフェースを実装する Controller のパスに、契約の {@code servers.url} を付ける。
     *
     * <p>生成したインターフェースのパスは {@code servers.url} を含まないため。
     * {@code admin.yaml} は {@code /api/admin}、{@code store.yaml} は {@code /api/store}。
     *
     * @param configurer パスの対応の設定
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PREFIXES.forEach((apiPackage, prefix) ->
                configurer.addPathPrefix(prefix, controller -> implementsApiOf(controller, apiPackage)));
    }

    /**
     * クエリパラメータの文字列を、OpenAPI から生成した列挙型にする変換を登録する。
     *
     * <p>生成した列挙型は、API の値（例：{@code price_asc}）と定数名（{@code PRICE_ASC}）が違うことがある。
     * Spring MVC の既定の変換は定数名で探すので、生成した列挙型の {@code fromValue} で変換する。
     *
     * @param registry 登録先
     */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumConverterFactory());
    }

// ============================== プライベートメソッド ==============================

    /**
     * Controller が、指定したパッケージの生成インターフェースを実装しているかを返す。
     *
     * @param controller Controller のクラス
     * @param apiPackage 生成インターフェースのパッケージ
     * @return 実装していれば true
     */
    private static boolean implementsApiOf(Class<?> controller, String apiPackage) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(controller))
                .anyMatch(api -> api.getPackageName().equals(apiPackage));
    }

    /**
     * 文字列から列挙型への変換
     *
     * <p>{@code fromValue} があればそれで、なければ定数名で変換する。値が合わなければ {@link IllegalArgumentException} を投げる。
     */
    // 変換先の列挙型は実行時にしか分からず、型引数を静的に確認できないので、生の型で変換する
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

        /**
         * 変換先の列挙型への変換を返す。
         *
         * @param targetType 変換先の列挙型
         * @return 変換
         */
        @Override
        public <T extends Enum> @NonNull Converter<String, T> getConverter(@NonNull Class<T> targetType) {
            Method fromValue = findFromValue(targetType);
            if (fromValue == null) {
                return source -> (T) Enum.valueOf(targetType, source.trim());
            }
            return source -> (T) invoke(fromValue, source);
        }

        /**
         * 生成した列挙型の {@code fromValue} を探す。
         *
         * @param targetType 変換先の列挙型
         * @return {@code fromValue}。なければ null
         */
        private static @Nullable Method findFromValue(Class<?> targetType) {
            try {
                return targetType.getMethod("fromValue", String.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /**
         * {@code fromValue} を呼ぶ。
         *
         * <p>中で投げた実行時の例外は、そのまま投げ直す。
         *
         * @param fromValue 生成した列挙型の {@code fromValue}
         * @param source    変換する文字列
         * @return 変換した値
         */
        private static Object invoke(Method fromValue, String source) {
            try {
                return fromValue.invoke(null, source);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalArgumentException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
