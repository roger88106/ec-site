package com.ecsite.contract;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API の契約のリクエストの制約の漏れを検出する契約テスト（docs/rules/backend-coding.md 第 3.1 節）。<br />
 * リクエスト（パス・クエリの引数と本文）の数値の項目に範囲（下限と上限。上限を求めない項目は理由を付けて除く）があり、
 * 文字の項目に長さか形式（{@code maxLength}・{@code pattern}・{@code format}・{@code enum}）があることを確認する。
 */
class OpenApiRequestConstraintTest {

    /**
     * 操作を表すキー（パスの項目のうち、操作でないもの（parameters など）を除くため）
     */
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch");

    /**
     * 数値の項目のうち、上限を求めないもの（項目の名前と理由）。<br />
     * 上限を求めるのは、値を計算に使うと桁あふれや DB の範囲の超過で 500 になるため。計算に使わない項目だけをここに置く
     */
    private static final Map<String, String> NO_MAXIMUM = Map.of(
            "version", "楽観ロックの版。サーバーが返した値を送り返すだけで、合わなければ 409 になる",
            "page", "ページ番号。読み飛ばす件数は long で計算するので、大きくても空のページになるだけ",
            "minPrice", "絞り込みの値。税込価格と比べるだけで計算に使わない",
            "maxPrice", "絞り込みの値。税込価格と比べるだけで計算に使わない",
            "expectedTotal", "画面に表示した総額。サーバーが計算した総額と比べるだけで計算に使わない");

    @ParameterizedTest
    @ValueSource(strings = {"admin.yaml", "store.yaml"})
    @DisplayName("リクエストの数値の項目に下限と上限、文字の項目に長さか形式がある")
    void requestFieldsAreConstrained(String file) {
        Map<String, Object> document = Contracts.openApi(file);
        List<String> missing = new ArrayList<>();

        // パスごと、操作ごとに、引数と本文の項目をたどる
        for (var path : map(document.get("paths")).entrySet()) {
            Map<String, Object> pathItem = map(path.getValue());
            for (var operation : pathItem.entrySet()) {
                if (!METHODS.contains(operation.getKey())) {
                    continue;
                }
                Map<String, Object> op = map(operation.getValue());
                String where = operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey();
                List<Object> parameters = new ArrayList<>(list(pathItem.get("parameters")));
                parameters.addAll(list(op.get("parameters")));
                for (Object parameter : parameters) {
                    Map<String, Object> resolved = resolve(document, map(parameter));
                    walk(document, resolved.get("schema"), where + " " + resolved.get("in") + ":" + resolved.get("name"),
                            new HashSet<>(), missing);
                }
                if (op.get("requestBody") != null) {
                    Map<String, Object> body = resolve(document, map(op.get("requestBody")));
                    for (Object media : map(body.get("content")).values()) {
                        walk(document, map(media).get("schema"), where + " body", new HashSet<>(), missing);
                    }
                }
            }
        }

        assertThat(missing).as("制約のない項目").isEmpty();
    }

    /**
     * スキーマをたどり、制約のない数値と文字の項目を集める。
     *
     * @param document 契約の全体（$ref の解決に使う）
     * @param schema   スキーマ
     * @param where    項目の場所（報告に使う）
     * @param visited  たどった $ref（再帰する型で止めるため）
     * @param missing  制約のない項目を追加する先
     */
    private static void walk(Map<String, Object> document, Object schema, String where, Set<String> visited,
            List<String> missing) {
        if (schema == null) {
            return;
        }
        Map<String, Object> raw = map(schema);
        if (raw.get("$ref") instanceof String ref && !visited.add(ref)) {
            return;
        }
        Map<String, Object> s = resolve(document, raw);
        for (String combinator : List.of("allOf", "oneOf", "anyOf")) {
            for (Object part : list(s.get(combinator))) {
                walk(document, part, where, visited, missing);
            }
        }
        String type = String.valueOf(s.get("type"));
        switch (type) {
            case "object" -> map(s.get("properties")).forEach((name, property) ->
                    walk(document, property, where + "." + name, new HashSet<>(visited), missing));
            case "array" -> walk(document, s.get("items"), where + "[]", visited, missing);
            case "integer", "number" -> {
                String name = where.substring(Math.max(where.lastIndexOf('.'), where.lastIndexOf(':')) + 1);
                if (!s.containsKey("minimum") || (!s.containsKey("maximum") && !NO_MAXIMUM.containsKey(name))) {
                    missing.add(where + "（" + type + "：下限か上限がない）");
                }
            }
            case "string" -> {
                if (!s.containsKey("maxLength") && !s.containsKey("pattern") && !s.containsKey("format")
                        && !s.containsKey("enum")) {
                    missing.add(where + "（string：長さも形式もない）");
                }
            }
            default -> {
                if (s.get("properties") != null) {
                    map(s.get("properties")).forEach((name, property) ->
                            walk(document, property, where + "." + name, new HashSet<>(visited), missing));
                }
            }
        }
    }

    /**
     * {@code $ref}（同じファイルの中への参照）をたどった先を返す。
     *
     * @param document 契約の全体
     * @param node     スキーマか引数か本文
     * @return 参照でなければそのもの、参照なら参照の先
     */
    private static Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> node) {
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
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    /**
     * YAML の値を List として返す。
     *
     * @param value YAML の値
     * @return List。null なら空
     */
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
