package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契約をたどる補助 {@link OpenApiNodes} の単体テスト
 *
 * <p>契約のテストが共有するたどり方なので、たどり方の誤りで検査の対象が漏れないことを確認する。
 */
class OpenApiNodesTest {

    // ============================== 定数 ==============================

    /**
     * 8 つの HTTP メソッドの操作と、操作でないパスの項目を持つ契約
     */
    private static final String ALL_METHODS = """
            paths:
              /items:
                summary: 品目
                parameters:
                  - {name: id, in: query, schema: {type: string}}
                get: {operationId: get}
                put: {operationId: put}
                post: {operationId: post}
                delete: {operationId: delete}
                options: {operationId: options}
                head: {operationId: head}
                patch: {operationId: patch}
                trace: {operationId: trace}
            """;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("操作のたどり方")
    class Operations {

        @Test
        @DisplayName("OpenAPI の 8 つの HTTP メソッドの全てを、操作として取り出す")
        void includesAllHttpMethods() {
            // 準備：8 つのメソッドの操作を持つ契約
            var document = OpenApiNodes.parse(ALL_METHODS);

            // 操作：操作を取り出す
            var operations = OpenApiNodes.operations(document);

            // 確認：8 つのメソッドの操作が、契約の順に並ぶ
            assertThat(operations).extracting(OpenApiNodes.Operation::name).containsExactly(
                    "GET /items", "PUT /items", "POST /items", "DELETE /items",
                    "OPTIONS /items", "HEAD /items", "PATCH /items", "TRACE /items");
        }

        @Test
        @DisplayName("パスの項目のうち、操作でないもの（共通の引数、説明）は取り出さない")
        void excludesNonOperationKeys() {
            // 準備：8 つのメソッドの操作のほかに、summary と parameters を持つパスの契約
            var document = OpenApiNodes.parse(ALL_METHODS);

            // 操作：操作を取り出す
            var operations = OpenApiNodes.operations(document);

            // 確認：取り出した操作は、全て operationId を持つ操作の定義
            assertThat(operations).hasSize(8)
                    .allSatisfy(operation -> assertThat(operation.node()).containsKey("operationId"));
        }

        @Test
        @DisplayName("操作から、パス共通の引数を読める")
        void keepsPathItem() {
            // 準備：パス共通の引数を持つ契約
            var document = OpenApiNodes.parse(ALL_METHODS);

            // 操作：最初の操作を取り出す
            var operation = OpenApiNodes.operations(document).getFirst();

            // 確認：操作の持つパスの項目から、パス共通の引数が 1 つ読める
            assertThat(OpenApiNodes.list(operation.pathItem().get("parameters"))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("参照のたどり方")
    class Resolve {

        @Test
        @DisplayName("参照の先が参照のときも、参照でないところまでたどる")
        void followsChainedReferences() {
            // 準備：A が B を、B が実体を参照する契約
            var document = OpenApiNodes.parse("""
                    components:
                      schemas:
                        A: {$ref: '#/components/schemas/B'}
                        B: {$ref: '#/components/schemas/C'}
                        C: {type: string, maxLength: 10}
                    """);

            // 操作：A の参照をたどる
            var resolved = OpenApiNodes.resolve(document, Map.of("$ref", "#/components/schemas/A"));

            // 確認：実体の C の上限が読める
            assertThat(resolved).containsEntry("maxLength", 10);
        }
    }
}
