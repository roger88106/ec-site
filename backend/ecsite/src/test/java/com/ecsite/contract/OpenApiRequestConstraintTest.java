package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ecsite.contract.OpenApiNodes.list;
import static com.ecsite.contract.OpenApiNodes.map;
import static com.ecsite.contract.OpenApiNodes.resolve;
import static java.util.Map.entry;
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

    // ============================== 定数 ==============================

    /**
     * 上限を求めない理由：楽観ロックのバージョン
     */
    private static final String VERSION = "楽観ロックのバージョン。サーバーが返した値を送り返すだけで、合わなければ 409 になる";

    /**
     * 上限を求めない理由：ページ番号
     */
    private static final String PAGE = "ページ番号。読み飛ばす件数は long で計算するので、大きくても空のページになるだけ";

    /**
     * 上限を求めない理由：価格の絞り込み
     */
    private static final String PRICE_FILTER = "絞り込みの値。税込価格と比べるだけで計算に使わない";

    /**
     * 上限を求めない理由：画面に表示した総額
     */
    private static final String EXPECTED_TOTAL = "画面に表示した総額。サーバーが計算した総額と比べるだけで計算に使わない";

    /**
     * 数値の項目のうち、上限を求めないもの
     *
     * <p>鍵は操作と項目（報告の場所と同じ形。{@code GET /products query:page} など）、値は理由。
     * 上限を求めるのは、値を計算に使うと桁あふれや DB の範囲の超過で 500 になるため。計算に使わない項目だけをここに置く。
     * 項目の名前だけで除くと、同じ名前の、計算に使う項目まで除くので、操作ごとに書く。2 つの契約で同じ操作と項目があれば、1 つの鍵で両方を除く。
     */
    private static final Map<String, String> NO_MAXIMUM = Map.ofEntries(
            // 管理 API
            entry("PUT /tax-categories/{taxCategoryId}/rates body.version", VERSION),
            entry("PUT /categories/{categoryId} body.version", VERSION),
            entry("PUT /products/{productId} body.version", VERSION),
            entry("DELETE /products/{productId} query:version", VERSION),
            entry("PUT /products/{productId}/publication-period body.version", VERSION),
            entry("POST /products/{productId}/variants body.version", VERSION),
            entry("PUT /products/{productId}/variants/order body.version", VERSION),
            entry("DELETE /products/{productId}/variants/{variantId} query:version", VERSION),
            entry("POST /products/{productId}/images body.version", VERSION),
            entry("PUT /products/{productId}/images/order body.version", VERSION),
            entry("DELETE /products/{productId}/images/{imageId} query:version", VERSION),
            entry("PUT /products/{productId}/variants/{variantId}/image body.version", VERSION),
            entry("GET /inventory/variants/{variantId}/adjustments query:page", PAGE),
            entry("GET /orders query:page", PAGE),
            // 管理 API と店舗 API の両方
            entry("GET /products query:page", PAGE),
            // 店舗 API
            entry("GET /products query:minPrice", PRICE_FILTER),
            entry("GET /products query:maxPrice", PRICE_FILTER),
            entry("GET /products/search query:page", PAGE),
            entry("POST /checkouts/{checkoutId}/order body.expectedTotal", EXPECTED_TOTAL),
            entry("GET /me/orders query:page", PAGE));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("契約のリクエストの制約")
    class Contract {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"admin.yaml", "store.yaml"})
        @DisplayName("リクエストの数値の項目に下限と上限、文字の項目に長さか形式がある")
        void requestFieldsAreConstrained(String file) {
            // 操作：契約の数値と文字の項目を検査する
            var scan = scan(Contracts.openApi(file), NO_MAXIMUM);

            // 確認：制約のない項目がない
            assertThat(scan.missing()).as("制約のない項目").isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"admin.yaml", "store.yaml"})
        @DisplayName("検査は契約の数値と文字の項目を 1 件以上たどる（たどり方の誤りで、何も調べずに通らない）")
        void scansFields(String file) {
            // 操作と確認：たどった項目が 1 件以上ある
            assertThat(scan(Contracts.openApi(file), NO_MAXIMUM).scanned()).isPositive();
        }

        @Test
        @DisplayName("上限を求めない項目の一覧の全てが、契約の上限のない項目にある（なくなった項目を一覧に残さない）")
        void noMaximumEntriesExist() {
            // 操作：2 つの契約で、上限がなく、一覧で除いた項目を集める
            Set<String> exempted = new TreeSet<>();
            for (String file : List.of("admin.yaml", "store.yaml")) {
                exempted.addAll(scan(Contracts.openApi(file), NO_MAXIMUM).exempted());
            }

            // 確認：集めた項目が、一覧の項目と同じ
            assertThat(exempted).isEqualTo(new TreeSet<>(NO_MAXIMUM.keySet()));
        }
    }

    @Nested
    @DisplayName("検査が誤りを検出できること")
    class Detection {

        @Test
        @DisplayName("制約のない数値と文字の項目を見つけ、制約のある項目と上限を求めない項目は見つけない")
        void detectsUnconstrainedFields() {
            // 準備：制約のない項目 4 つ、制約のある項目 3 つ、上限を求めない項目 1 つを持つ契約
            var document = OpenApiNodes.parse("""
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

            // 操作：1 つの項目を除いて検査する
            var scan = scan(document, Map.of("PUT /items/{id} query:page", PAGE));

            // 確認：制約のない 4 つだけを見つけ、8 つの項目をたどる
            assertThat(scan.missing()).containsExactlyInAnyOrder(
                    "PUT /items/{id} query:limit（integer：下限か上限がない）",
                    "PUT /items/{id} body.name（string：長さも形式もない）",
                    "PUT /items/{id} body.price（integer：下限か上限がない）",
                    "PUT /items/{id} body.tags[]（string：長さも形式もない）");
            assertThat(scan.scanned()).isEqualTo(8);
        }

        @Test
        @DisplayName("上限を求めない項目は操作と項目の組で除き、同じ名前の、別の操作の項目は除かない")
        void exemptsByOperationAndField() {
            // 準備：2 つの操作が、上限のない同じ名前の項目を持つ契約
            var document = OpenApiNodes.parse("""
                    paths:
                      /items:
                        get:
                          parameters:
                            - {name: page, in: query, schema: {type: integer, minimum: 1}}
                      /orders:
                        get:
                          parameters:
                            - {name: page, in: query, schema: {type: integer, minimum: 1}}
                    """);

            // 操作：片方の操作の項目だけを除く
            var scan = scan(document, Map.of("GET /items query:page", PAGE));

            // 確認：除くのは、指定した操作の項目だけ
            assertThat(scan.missing()).containsExactly("GET /orders query:page（integer：下限か上限がない）");
            assertThat(scan.exempted()).containsExactly("GET /items query:page");
        }
    }

    // ============================== 検査 ==============================

    /**
     * 検査の結果
     *
     * @param missing  制約のない項目
     * @param scanned  たどった数値と文字の項目の数
     * @param exempted 上限がなく、上限を求めない項目の一覧で除いた項目
     */
    private record Scan(List<String> missing, int scanned, Set<String> exempted) {
    }

    /**
     * 検査の途中の状態
     *
     * @param document  契約の全体（$ref の解決に使う）
     * @param noMaximum 上限を求めない項目（鍵は操作と項目）
     * @param missing   制約のない項目を追加する先
     * @param exempted  除いた項目を追加する先
     * @param scanned   たどった数値と文字の項目の数を数える先
     */
    private record Walker(Map<String, Object> document, Map<String, String> noMaximum, List<String> missing,
            Set<String> exempted, AtomicInteger scanned) {
    }

    /**
     * 契約の全ての操作の引数と本文をたどり、制約のない数値と文字の項目を集める。
     *
     * @param document  契約の全体
     * @param noMaximum 上限を求めない項目（鍵は操作と項目）
     * @return 検査の結果
     */
    private static Scan scan(Map<String, Object> document, Map<String, String> noMaximum) {
        var walker = new Walker(document, noMaximum, new ArrayList<>(), new TreeSet<>(), new AtomicInteger());

        // 操作ごとに、パス共通と操作の引数、本文の項目をたどる
        for (var operation : OpenApiNodes.operations(document)) {
            List<Object> parameters = new ArrayList<>(list(operation.pathItem().get("parameters")));
            parameters.addAll(list(operation.node().get("parameters")));
            for (Object parameter : parameters) {
                Map<String, Object> resolved = resolve(document, map(parameter));
                walk(walker, resolved.get("schema"),
                        operation.name() + " " + resolved.get("in") + ":" + resolved.get("name"), new HashSet<>());
            }
            if (operation.node().get("requestBody") != null) {
                Map<String, Object> body = resolve(document, map(operation.node().get("requestBody")));
                for (Object media : map(body.get("content")).values()) {
                    walk(walker, map(media).get("schema"), operation.name() + " body", new HashSet<>());
                }
            }
        }
        return new Scan(List.copyOf(walker.missing()), walker.scanned().get(), Set.copyOf(walker.exempted()));
    }

    /**
     * スキーマをたどり、制約のない数値と文字の項目を集める。
     *
     * @param walker  検査の途中の状態
     * @param schema  スキーマ
     * @param where   項目の場所（報告と、上限を求めない項目の鍵に使う）
     * @param visited たどった $ref（再帰する型で止めるため）
     */
    private static void walk(Walker walker, Object schema, String where, Set<String> visited) {
        if (schema == null) {
            return;
        }
        Map<String, Object> raw = map(schema);
        if (raw.get("$ref") instanceof String ref && !visited.add(ref)) {
            return;
        }
        Map<String, Object> s = resolve(walker.document(), raw);
        for (String combinator : List.of("allOf", "oneOf", "anyOf")) {
            for (Object part : list(s.get(combinator))) {
                walk(walker, part, where, visited);
            }
        }
        String type = String.valueOf(s.get("type"));
        switch (type) {
            case "array" -> walk(walker, s.get("items"), where + "[]", visited);
            case "integer", "number" -> {
                walker.scanned().incrementAndGet();
                boolean exempt = !s.containsKey("maximum") && walker.noMaximum().containsKey(where);
                if (exempt) {
                    walker.exempted().add(where);
                }
                if (!s.containsKey("minimum") || (!s.containsKey("maximum") && !exempt)) {
                    walker.missing().add(where + "（" + type + "：下限か上限がない）");
                }
            }
            case "string" -> {
                walker.scanned().incrementAndGet();
                if (!s.containsKey("maxLength") && !s.containsKey("pattern") && !s.containsKey("format")
                        && !s.containsKey("enum")) {
                    walker.missing().add(where + "（string：長さも形式もない）");
                }
            }
            // object と、type のない（properties だけの）スキーマは、properties の各項目を調べる
            default -> map(s.get("properties")).forEach((name, property) ->
                    walk(walker, property, where + "." + name, new HashSet<>(visited)));
        }
    }
}
