package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * OpenAPI の操作の一覧の読み方の検査。<br />
 * 操作ごとの値は、管理 API なら権限、店舗 API なら token の要否で、読む側が決める（ここでは operationId を値にする）。
 */
class OpenApiOperationsTest {

    /**
     * 操作の一覧。具体的なパスと、変数を含むパスが重なる
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

    /**
     * 操作の一覧（値は operationId）
     */
    private final OpenApiOperations<String> operations = OpenApiOperations.load(
            new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)), (name, operation) -> (String) operation.get("operationId"));

    @Test
    @DisplayName("パスは servers の URL を前に付けて照合する")
    void prefixesServerUrl() {
        assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
        assertThat(operations.find("GET", "/categories/order")).isEmpty();
    }

    @Test
    @DisplayName("具体的なパスを、変数を含むパスより先に照合する")
    void matchesSpecificPathFirst() {
        assertThat(operations.find("GET", "/api/admin/categories/order")).hasValue("getCategoryOrder");
        assertThat(operations.find("GET", "/api/admin/categories/c1")).hasValue("getCategory");
    }

    @Test
    @DisplayName("メソッドも照合し、パス共通の parameters は操作として読まない")
    void matchesMethod() {
        assertThat(operations.find("PUT", "/api/admin/categories/order")).hasValue("putCategoryOrder");
        assertThat(operations.find("DELETE", "/api/admin/categories/order")).isEmpty();
    }

    @Test
    @DisplayName("契約にない操作は空")
    void unknownOperationIsEmpty() {
        assertThat(operations.find("GET", "/api/admin/unknown")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "Get"})
    @DisplayName("パスの下に小文字でない HTTP メソッドがあると、契約のファイル、パス、キーを示して読み込みに失敗する")
    void rejectsNonLowercaseMethod(String key) {
        assertThatIllegalStateException()
                .isThrownBy(() -> load(pathItem(key + ":\n      operationId: getCategories")))
                .withMessageContainingAll("admin.yaml", "/categories", key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gett", "requestBody", "security"})
    @DisplayName("パスの下に HTTP メソッドと OpenAPI の Path Item の項目の外のキーがあると、契約のファイル、パス、キーを示して読み込みに失敗する")
    void rejectsUnknownKey(String key) {
        assertThatIllegalStateException()
                .isThrownBy(() -> load(pathItem(key + ":\n      operationId: getCategories")))
                .withMessageContainingAll("admin.yaml", "/categories", key);
    }

    @Test
    @DisplayName("パスの下の OpenAPI の Path Item の項目と x- で始まる拡張は、操作として読まずに認める")
    void acceptsPathItemFields() {
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

        assertThat(loaded.find("GET", "/api/admin/categories")).hasValue("getCategories");
    }

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
