package com.ecsite.contract;

import java.util.Map;

/**
 * 契約（OpenAPI）の YAML を読んだ Map をたどる補助。契約のテストが同じ処理を重複して書かないために置く。
 */
final class OpenApiNodes {

    /**
     * インスタンスを作らない。
     */
    private OpenApiNodes() {
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
}
