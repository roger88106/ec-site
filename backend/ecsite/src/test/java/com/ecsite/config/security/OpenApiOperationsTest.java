package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * OpenAPI の操作の一覧の読み方の検査
 *
 * <p>操作ごとの値は、管理 API なら権限、店舗 API なら token の要否で、読む側が決める（ここでは operationId を値にする）。
 */
class OpenApiOperationsTest {

    // ============================== 定数 ==============================

    /**
     * 操作の一覧
     *
     * <p>具体的なパスと、変数を含むパスが重なる。
     */
    private static final String SPEC = """
            servers:
              - url: /api/admin
            paths:
              /categories/{categoryId}:
                parameters:
                  - name: categoryId
                    in: path
                get:
                  operationId: getCategory
              /categories/order:
                get:
                  operationId: getCategoryOrder
                put:
                  operationId: putCategoryOrder
            """;

    // ============================== テストの対象 ==============================

    /**
     * 操作の一覧（値は operationId）
     */
    private final OpenApiOperations<String> operations = OpenApiOperations.load(
            new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)), (name, operation) -> (String) operation.get("operationId"));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("操作の照合")
    class Find {

        @Test
        @DisplayName("パスは servers の URL を前に付けて照合する")
        void prefixesServerUrl() {
            // 操作と確認：servers の URL を付けたパスで見つかり、付けないパスでは見つからない
            assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
            assertThat(operations.find("GET", "/categories/order")).isEmpty();
        }

        @Test
        @DisplayName("具体的なパスを、変数を含むパスより先に照合する")
        void matchesSpecificPathFirst() {
            // 操作と確認：両方に合うパスは具体的なパスの操作になり、変数だけに合うパスは変数を含むパスの操作になる
            assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
            assertThat(operations.find("GET", "/api/admin/categories/c1")).hasValue("getCategory");
        }

        @Test
        @DisplayName("メソッドも照合する")
        void matchesMethod() {
            // 操作と確認：同じパスでも、契約にあるメソッドだけが見つかる
            assertThat(operations.find("PUT", "/api/admin/categories/order")).hasValue("putCategoryOrder");
            assertThat(operations.find("DELETE", "/api/admin/categories/order")).isEmpty();
        }

        @Test
        @DisplayName("契約にない操作は空")
        void unknownOperationIsEmpty() {
            // 操作と確認：契約にないパスは見つからない
            assertThat(operations.find("GET", "/api/admin/unknown")).isEmpty();
        }
    }

    @Nested
    @DisplayName("パスの下のキーの検証")
    class PathItemKeys {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"GET", "Get"})
        @DisplayName("パスの下に小文字でない HTTP メソッドがあると、契約のファイル、パス、キーを示して読み込みに失敗する")
        void rejectsNonLowercaseMethod(String key) {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイル・パス・キーがある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(pathItem(key + ":\n      operationId: getCategories")))
                    .withMessageContainingAll("admin.yaml", "/categories", key);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"gett", "requestBody", "security"})
        @DisplayName("パスの下に HTTP メソッドと OpenAPI の Path Item の項目の外のキーがあると、契約のファイル、パス、キーを示して読み込みに失敗する")
        void rejectsUnknownKey(String key) {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイル・パス・キーがある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(pathItem(key + ":\n      operationId: getCategories")))
                    .withMessageContainingAll("admin.yaml", "/categories", key);
        }

        @Test
        @DisplayName("パスの下の OpenAPI の Path Item の項目（パス共通の parameters など）と x- で始まる拡張は、操作として読まずに認める")
        void acceptsPathItemFields() {
            // 操作：Path Item の全ての項目と拡張を置いた契約を読む
            var loaded = load(pathItem("""
                    summary: カテゴリ
                        description: カテゴリの一覧
                        servers:
                          - url: /api/admin
                        parameters:
                          - name: q
                            in: query
                        $ref: '#/components/pathItems/Categories'
                        x-note: 補足
                        get:
                          operationId: getCategories"""));

            // 確認：読み込みに成功し、操作だけが見つかる
            assertThat(loaded.find("GET", "/api/admin/categories")).hasValue("getCategories");
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * {@code /categories} の Path Item に、渡したキーと値を置いた契約を作る。
     *
     * @param fields Path Item の中身（2 行目以降は Path Item の字下げで書く）
     * @return 契約
     */
    private static String pathItem(String fields) {
        return """
                servers:
                  - url: /api/admin
                paths:
                  /categories:
                    %s
                """.formatted(fields);
    }

    /**
     * 契約を {@code admin.yaml} という名前で読み込む（値は operationId）。
     *
     * @param spec 契約
     * @return 操作の一覧
     */
    private static OpenApiOperations<String> load(String spec) {
        return OpenApiOperations.load(
                new ByteArrayResource(spec.getBytes(StandardCharsets.UTF_8), "admin.yaml"),
                (name, operation) -> (String) operation.get("operationId"));
    }
}
