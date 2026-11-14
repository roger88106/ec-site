package com.ecsite;

import jakarta.validation.Validation;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 設定のテストの補助
 *
 * <p>設定ファイルの読み込みと、設定の型への値の当てはめを、各テストに重複して書かないために置く。
 */
public final class TestSettings {

    /**
     * インスタンスを作らない。
     */
    private TestSettings() {
    }

    /**
     * 設定ファイルを重ねて読んだ値を返す。
     *
     * <p>起動のときと同じ順（{@code application.yml}、指定した profile の順）に重ね、後のものが先のものを上書きするとして読む。後のファイルで値が null のキーは、先の値を消さず、無視する。
     *
     * @param profiles 指定する profile（順に重ねる。{@code application-<profile>.yml} を読む）
     * @return 設定の名前（階層は . でつなぐ。配列は {@code a[0]} のように添字付きの名前になる）と、文字列にした値。
     * どのファイルでも値が null のキー（{@code key:} だけ）は含めない
     * @throws UncheckedIOException 設定ファイルを読めないとき
     */
    public static Map<String, String> load(String... profiles) {
        List<String> files = Stream.concat(Stream.of("application.yml"),
                Arrays.stream(profiles).map(profile -> "application-" + profile + ".yml")).toList();
        Map<String, String> values = new HashMap<>();
        try {
            for (String file : files) {
                for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                    if (source instanceof EnumerablePropertySource<?> enumerable) {
                        for (String name : enumerable.getPropertyNames()) {
                            Object value = enumerable.getProperty(name);
                            if (value != null) {
                                values.put(name, String.valueOf(value));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("設定ファイルを読めない: " + files, e);
        }
        return values;
    }

    /**
     * 接頭辞の下の値を、設定の型に当てはめる。
     *
     * <p>Spring を起動せずに、設定の型の既定値と値の変換を確認するために使う。
     *
     * @param prefix 設定の接頭辞
     * @param type   設定の型
     * @param values 接頭辞の下の名前と値（空なら既定値のまま）
     * @param <T>    設定の型
     * @return 値を当てはめた設定
     */
    public static <T> T bind(String prefix, Class<T> type, Map<String, String> values) {
        var source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put(prefix + "." + key, value));
        return new Binder(source).bindOrCreate(prefix, type);
    }

    /**
     * 接頭辞の下の値を設定の型に当てはめ、型の制約（{@code @Min} など）で検証する。
     *
     * <p>起動のときと同じく、制約に反する値は例外にする。値の範囲の検証を確認するときに使う。
     *
     * @param prefix 設定の接頭辞
     * @param type   設定の型
     * @param values 接頭辞の下の名前と値（空なら既定値のまま）
     * @param <T>    設定の型
     * @return 値を当てはめた設定
     * @throws BindException 値を変換できないか、制約に反するとき（原因は {@link BindValidationException}）
     */
    public static <T> T bindValidated(String prefix, Class<T> type, Map<String, String> values) {
        var source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put(prefix + "." + key, value));
        return bindValidated(source, prefix, type);
    }

    /**
     * 設定ファイルを起動のときと同じ順に重ねて読み、接頭辞の下の値を設定の型に当てはめ、検証する。
     *
     * <p>設定の「既定」を確認するときに使う。設定の型の既定値だけでなく、{@code application.yml} などに書いた値を含めて、
     * 起動したアプリが使う値になる（{@link #load} と同じ読み方。環境変数は読まない）。
     *
     * @param prefix   設定の接頭辞
     * @param type     設定の型
     * @param profiles 指定する profile（順に重ねる）
     * @param <T>      設定の型
     * @return 値を当てはめた設定
     * @throws BindException 値を変換できないか、制約に反するとき
     */
    public static <T> T loadAndBind(String prefix, Class<T> type, String... profiles) {
        return bindValidated(new MapConfigurationPropertySource(load(profiles)), prefix, type);
    }

    /**
     * 設定の値を設定の型に当てはめ、型の制約で検証する。
     */
    private static <T> T bindValidated(MapConfigurationPropertySource source, String prefix, Class<T> type) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var handler = new ValidationBindHandler(new SpringValidatorAdapter(factory.getValidator()));
            return new Binder(source).bindOrCreate(prefix, Bindable.of(type), handler);
        }
    }
}
