package com.ecsite.config.security;

import org.springframework.core.io.Resource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * OpenAPI の契約の操作（メソッドとパス）ごとの値。管理 API の権限と、店舗 API の token の要否に使う。<br />
 * パスは {@code servers.url} を前に付け、具体的なパスを変数を含むパスより先に照合する
 * （{@code /categories/order} を {@code /categories/{categoryId}} より先）。
 *
 * @param <T> 操作ごとの値
 */
final class OpenApiOperations<T> {

    /**
     * OpenAPI で操作を表す HTTP メソッド
     */
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

    /**
     * OpenAPI の Path Item の項目のうち、操作でないもの。拡張（{@code x-} で始まるもの）も認める
     */
    private static final Set<String> PATH_ITEM_FIELDS = Set.of("summary", "description", "servers", "parameters", "$ref");

    /**
     * OpenAPI の拡張のキーの接頭辞
     */
    private static final String EXTENSION_PREFIX = "x-";

    /**
     * 操作
     *
     * @param method  HTTP メソッド（大文字）
     * @param pattern パス（{@code servers.url} を含む）
     * @param value   操作ごとの値
     * @param <T>     操作ごとの値の型
     */
    private record Operation<T>(String method, PathPattern pattern, T value) {
    }

    /**
     * 操作の一覧（パスの具体的なものが先）
     */
    private final List<Operation<T>> operations;

    /**
     * @param operations 操作の一覧（パスの具体的なものが先）
     */
    private OpenApiOperations(List<Operation<T>> operations) {
        this.operations = operations;
    }

    /**
     * 契約を読み込む。
     *
     * @param resource 契約（{@code admin.yaml} など）
     * @param valueOf  操作の名前（{@code GET /products} の形）と操作の定義から、操作ごとの値を作る。起動を止めるときは例外を投げる
     * @param <T>      操作ごとの値の型
     * @return 操作ごとの値
     * @throws IllegalStateException パスの下に、小文字の HTTP メソッド、Path Item の項目、拡張のどれでもないキーがあるとき
     */
    static <T> OpenApiOperations<T> load(Resource resource, BiFunction<String, Map<String, Object>, T> valueOf) {
        try (InputStream in = resource.getInputStream()) {
            return parse(resource.getDescription(), in, valueOf);
        } catch (IOException e) {
            throw new UncheckedIOException("OpenAPI の契約を読めない：" + resource, e);
        }
    }

    /**
     * 契約の操作を読み、パスの具体的なものが先になるように並べる。
     *
     * @param contract 契約のファイル（例外のメッセージに使う）
     */
    // YAML を読んだ汎用の Map を、契約（OpenAPI）の形が決まっているので、型を当てはめて読む
    @SuppressWarnings("unchecked")
    private static <T> OpenApiOperations<T> parse(String contract, InputStream in, BiFunction<String, Map<String, Object>, T> valueOf) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        var servers = (List<Map<String, Object>>) Objects.requireNonNull(root.get("servers"), "OpenAPI の契約には servers がある");
        var basePath = (String) servers.getFirst().get("url");
        var paths = (Map<String, Map<String, Object>>) Objects.requireNonNull(root.get("paths"), "OpenAPI の契約には paths がある");

        var parser = PathPatternParser.defaultInstance;
        var operations = new ArrayList<Operation<T>>();
        paths.forEach((path, item) -> item.forEach((key, value) -> {
            if (!HTTP_METHODS.contains(key)) {
                // パス共通の parameters などは操作ではない。誤記は、認可の条件のない操作になるので起動を止める
                checkPathItemField(contract, path, key);
                return;
            }
            var method = key.toUpperCase(Locale.ROOT);
            var operation = (Map<String, Object>) value;
            operations.add(new Operation<>(method, parser.parse(basePath + path), valueOf.apply(method + " " + path, operation)));
        }));
        operations.sort(Comparator.comparing(Operation::pattern, PathPattern.SPECIFICITY_COMPARATOR));
        return new OpenApiOperations<>(List.copyOf(operations));
    }

    /**
     * 操作でないパスの下のキーが、OpenAPI の Path Item の項目か拡張であることを確かめる。
     *
     * @param contract 契約のファイル
     * @param path     パス
     * @param key      パスの下のキー
     * @throws IllegalStateException 小文字でない HTTP メソッドか、Path Item の項目の外のキーのとき
     */
    private static void checkPathItemField(String contract, String path, String key) {
        if (HTTP_METHODS.contains(key.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "OpenAPI の契約 " + contract + " のパス " + path + " の HTTP メソッド " + key + " は小文字で書く");
        }
        if (!PATH_ITEM_FIELDS.contains(key) && !key.startsWith(EXTENSION_PREFIX)) {
            throw new IllegalStateException(
                    "OpenAPI の契約 " + contract + " のパス " + path + " に、HTTP メソッドと Path Item の項目の外のキー " + key + " がある");
        }
    }

    /**
     * 要求に合う操作の値を求める。
     *
     * @param method HTTP メソッド
     * @param path   アプリの中のパス（{@code /api/admin/...}）
     * @return 操作の値。契約にない要求なら空
     */
    Optional<T> find(String method, String path) {
        var container = PathContainer.parsePath(path);
        // メソッドとパスが合う最初の操作の値を返す
        return operations.stream()
                .filter(op -> op.method().equals(method) && op.pattern().matches(container))
                .map(Operation::value)
                .findFirst();
    }
}
