package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ecsite.contract.OpenApiNodes.map;
import static com.ecsite.contract.OpenApiNodes.resolve;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * API の契約のリクエストの制約の漏れを検出する契約テスト
 *
 * <p>バックエンドの書き方の「形の検証は presentation、業務の規則は domain」に沿う。
 *
 * <p>リクエスト（パス・クエリの引数と本文）の数値の項目に範囲（下限と上限。上限を求めない項目は理由を付けて除く）があり、
 * 文字の項目に長さか形式（{@code maxLength}・{@code pattern}・{@code format}・{@code enum}）があることを確認する。
 */
class OpenApiRequestConstraintTest {

    /**
     * 操作を表すキー
     *
     * <p>パスの項目のうち、操作でないもの（parameters など）を除くために使う。
     */
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch");

    /**
     * 数値の項目のうち、上限を求めないもの
     *
     * <p>項目の名前と理由を持つ。上限を求めるのは、値を計算に使うと桁あふれや DB の範囲の超過で 500 になるため。計算に使わない項目だけをここに置く
     */
    private static final Map<String, String> NO_MAXIMUM = Map.of(
            "version", "楽観ロックのバージョン。サーバーが返した値を送り返すだけで、合わなければ 409 になる",
            "page", "ページ番号。読み飛ばす件数は long で計算するので、大きくても空のページになるだけ",
            "minPrice", "絞り込みの値。税込価格と比べるだけで計算に使わない",
            "maxPrice", "絞り込みの値。税込価格と比べるだけで計算に使わない",
            "expectedTotal", "画面に表示した総額。サーバーが計算した総額と比べるだけで計算に使わない");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"admin.yaml", "store.yaml"})
    @DisplayName("リクエストの数値の項目に下限と上限、文字の項目に長さか形式がある")
    void requestFieldsAreConstrained(String file) {
        var scan = scan(Contracts.openApi(file));

        assertThat(scan.missing()).as("制約のない項目").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"admin.yaml", "store.yaml"})
    @DisplayName("検査は契約の数値と文字の項目を 1 件以上たどる（たどり方の誤りで、何も調べずに通らない）")
    void scansFields(String file) {
        assertThat(scan(Contracts.openApi(file)).scanned()).isPositive();
    }

    @Test
    @DisplayName("検査は、制約のない数値と文字の項目を見つけ、制約のある項目と上限を求めない項目は見つけない")
    void detectsUnconstrainedFields() {
        Map<String, Object> document = new Yaml().load("""
                paths:
                  /items/{id}:
                    parameters:
                      - {name: id, in: path, schema: {type: string, format: uuid}}
                    put:
                      parameters:
                        - {name: limit, in: query, schema: {type: integer}}
                        - {name: page, in: query, schema: {type: integer, minimum: 1}}
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: '#/components/schemas/Item'}
                components:
                  schemas:
                    Item:
                      type: object
                      properties:
                        name: {type: string}
                        code: {type: string, maxLength: 10}
                        price: {type: integer, minimum: 0}
                        quantity: {type: integer, minimum: 0, maximum: 99}
                        tags: {type: array, items: {type: string}}
                """);

        var scan = scan(document);

        assertThat(scan.missing()).containsExactlyInAnyOrder(
                "PUT /items/{id} query:limit（integer：下限か上限がない）",
                "PUT /items/{id} body.name（string：長さも形式もない）",
                "PUT /items/{id} body.price（integer：下限か上限がない）",
                "PUT /items/{id} body.tags[]（string：長さも形式もない）");
        assertThat(scan.scanned()).isEqualTo(8);
    }

    /**
     * 検査の結果
     *
     * @param missing 制約のない項目
     * @param scanned たどった数値と文字の項目の数
     */
    private record Scan(List<String> missing, int scanned) {
    }

    /**
     * 契約の全ての操作の引数と本文をたどり、制約のない数値と文字の項目を集める。
     *
     * @param document 契約の全体
     * @return 検査の結果
     */
    private static Scan scan(Map<String, Object> document) {
        List<String> missing = new ArrayList<>();
        var scanned = new AtomicInteger();

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
                            new HashSet<>(), missing, scanned);
                }
                if (op.get("requestBody") != null) {
                    Map<String, Object> body = resolve(document, map(op.get("requestBody")));
                    for (Object media : map(body.get("content")).values()) {
                        walk(document, map(media).get("schema"), where + " body", new HashSet<>(), missing, scanned);
                    }
                }
            }
        }
        return new Scan(List.copyOf(missing), scanned.get());
    }

    /**
     * スキーマをたどり、制約のない数値と文字の項目を集める。
     *
     * @param document 契約の全体（$ref の解決に使う）
     * @param schema   スキーマ
     * @param where    項目の場所（報告に使う）
     * @param visited  たどった $ref（再帰する型で止めるため）
     * @param missing  制約のない項目を追加する先
     * @param scanned  たどった数値と文字の項目の数を数える先
     */
    private static void walk(Map<String, Object> document, Object schema, String where, Set<String> visited,
            List<String> missing, AtomicInteger scanned) {
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
                walk(document, part, where, visited, missing, scanned);
            }
        }
        String type = String.valueOf(s.get("type"));
        switch (type) {
            case "array" -> walk(document, s.get("items"), where + "[]", visited, missing, scanned);
            case "integer", "number" -> {
                scanned.incrementAndGet();
                String name = where.substring(Math.max(where.lastIndexOf('.'), where.lastIndexOf(':')) + 1);
                if (!s.containsKey("minimum") || (!s.containsKey("maximum") && !NO_MAXIMUM.containsKey(name))) {
                    missing.add(where + "（" + type + "：下限か上限がない）");
                }
            }
            case "string" -> {
                scanned.incrementAndGet();
                if (!s.containsKey("maxLength") && !s.containsKey("pattern") && !s.containsKey("format")
                        && !s.containsKey("enum")) {
                    missing.add(where + "（string：長さも形式もない）");
                }
            }
            // object と、type のない（properties だけの）スキーマは、properties の各項目を調べる
            default -> {
                if (s.get("properties") != null) {
                    map(s.get("properties")).forEach((name, property) ->
                            walk(document, property, where + "." + name, new HashSet<>(visited), missing, scanned));
                }
            }
        }
    }

    /**
     * YAML の値を List として返す。
     *
     * @param value YAML の値
     * @return List。null なら空
     */
    // YAML を読んだ汎用の値を、契約（OpenAPI）の形が決まっているので、List として読む
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
