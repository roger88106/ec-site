package com.ecsite;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 設定のテストが使う補助。設定ファイルの読み込みと、設定の型への値の当てはめを、各テストに重複して書かないために置く。
 */
public final class TestSettings {

    /**
     * インスタンスを作らない。
     */
    private TestSettings() {
    }

    /**
     * 設定ファイルを重ねて読んだ値を返す。<br />
     * 起動のときと同じ順（{@code application.yml}、指定した profile の順）に重ね、後のものが先のものを上書きするとして読む。
     *
     * @param profiles 指定する profile（順に重ねる。{@code application-<profile>.yml} を読む）
     * @return 設定の名前（階層は . でつなぐ）と値
     */
    public static Map<String, String> load(String... profiles) {
        List<String> files = new ArrayList<>(List.of("application.yml"));
        for (String profile : profiles) {
            files.add("application-" + profile + ".yml");
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String file : files) {
                for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                    if (source instanceof EnumerablePropertySource<?> enumerable) {
                        for (String name : enumerable.getPropertyNames()) {
                            values.put(name, String.valueOf(enumerable.getProperty(name)));
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
     * 接頭辞の下の値を、設定の型に当てはめる。Spring を起動せずに、設定の型の既定値と値の変換を確認するために使う。
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
}
