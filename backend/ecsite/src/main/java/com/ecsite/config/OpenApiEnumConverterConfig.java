package com.ecsite.config;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * クエリパラメータの文字列を、OpenAPI から生成した列挙型に変換する。<br />
 * 生成した列挙型は、API 上の値（例：{@code price_asc}）と定数名（{@code PRICE_ASC}）が違うことがある。
 * Spring MVC の既定の変換は定数名で探すので、生成した列挙型が持つ {@code fromValue} で変換する。
 * {@code fromValue} を持たない列挙型は、既定どおり定数名で変換する。
 */
@Configuration
class OpenApiEnumConverterConfig implements WebMvcConfigurer {

    /**
     * 変換を登録する。
     *
     * @param registry 登録先
     */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumConverterFactory());
    }

    /**
     * 文字列から列挙型への変換。値が合わなければ {@link IllegalArgumentException}（400 になる）。
     */
    // 変換先の列挙型は実行時にしか分からず、Enum の型引数を静的に確認できないので、生の型で変換する
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        public <T extends Enum> @NonNull Converter<String, T> getConverter(@NonNull Class<T> targetType) {
            @Nullable Method fromValue = findFromValue(targetType);
            if (fromValue == null) {
                return source -> (T) Enum.valueOf(targetType, source.trim());
            }
            return source -> (T) invoke(fromValue, source);
        }

        /**
         * 生成した enum の fromValue を探す。
         *
         * @param targetType 変換する先の enum
         * @return fromValue。なければ null
         */
        private static @Nullable Method findFromValue(Class<?> targetType) {
            try {
                return targetType.getMethod("fromValue", String.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /**
         * fromValue を呼び、中で投げた実行時の例外はそのまま投げ直す。
         *
         * @param fromValue 生成した enum の fromValue
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
