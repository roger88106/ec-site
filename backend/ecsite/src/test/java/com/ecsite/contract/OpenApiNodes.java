package com.ecsite.contract;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 契約（OpenAPI）の YAML を読んだ Map をたどる補助
 *
 * <p>契約のテストが同じ処理を重複して書かないために置く。操作のたどり方（操作とみなす HTTP メソッドの集合）を、ここの {@link #operations(Map)} の 1 つにそろえる。
 */
final class OpenApiNodes {

    /**
     * OpenAPI で操作を表すキー（パスの項目のうち、HTTP メソッド）
     *
     * <p>パスの項目のうち、操作でないもの（{@code parameters}、{@code summary} など）を除くために使う。OpenAPI 3 が定める 8 つの全て。
     */
    static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    /**
     * インスタンスを作らない。
     */
    private OpenApiNodes() {
    }

    /**
     * 契約の 1 つの操作
     *
     * @param method   HTTP メソッド（大文字）
     * @param path     パス
     * @param pathItem パスの項目（パス共通の {@code parameters} を読むために使う）
     * @param node     操作の定義
     */
    record Operation(String method, String path, Map<String, Object> pathItem, Map<String, Object> node) {

        /**
         * 報告に使う操作の名前を返す。
         *
         * @return {@code GET /products} の形
         */
        String name() {
            return method + " " + path;
        }
    }

    /**
     * 契約の全ての操作を、契約に書かれた順に返す。
     *
     * @param document 契約の全体
     * @return 操作の一覧
     */
    static List<Operation> operations(Map<String, Object> document) {
        List<Operation> operations = new ArrayList<>();
        map(document.get("paths")).forEach((path, item) -> {
            Map<String, Object> pathItem = map(item);
            pathItem.forEach((key, value) -> {
                if (HTTP_METHODS.contains(key)) {
                    operations.add(new Operation(key.toUpperCase(Locale.ROOT), path, pathItem, map(value)));
                }
            });
        });
        return operations;
    }

    /**
     * YAML の文字列を、契約の全体として読む。
     *
     * <p>契約の誤りを検出できることを、YAML の文字列で書いた小さな契約で確認するテストが使う。
     *
     * @param yaml YAML の文字列
     * @return 読んだ契約
     */
    static Map<String, Object> parse(String yaml) {
        return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml));
    }

    /**
     * {@code $ref}（同じファイルの中への参照）をたどった先を返す。
     *
     * @param document 契約の全体
     * @param node     スキーマ、引数、本文など
     * @return 参照でなければそのもの、参照なら参照の先
     */
    static Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> node) {
        Map<String, Object> current = node;
        while (current.get("$ref") instanceof String ref) {
            Object target = document;
            for (String key : ref.substring(2).split("/", -1)) {
                target = map(target).get(key);
            }
            current = map(target);
        }
        return current;
    }

    /**
     * YAML の値を Map として返す。
     *
     * @param value YAML の値
     * @return Map。null なら空
     */
    // YAML を読んだ汎用の Map を、契約（OpenAPI）の形が決まっているので、型を当てはめて読む
    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    /**
     * YAML の値を List として返す。
     *
     * @param value YAML の値
     * @return List。null なら空
     */
    // YAML を読んだ汎用の値を、契約（OpenAPI）の形が決まっているので、List として読む
    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
