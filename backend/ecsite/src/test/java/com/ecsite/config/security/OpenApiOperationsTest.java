package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

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
            // 操作：$ref を除く Path Item の全ての項目と拡張を置いた契約を読む
            var loaded = load(pathItem("""
                    summary: カテゴリ
                        description: カテゴリの一覧
                        servers:
                          - url: /api/admin
                        parameters:
                          - name: q
                            in: query
                        x-note: 補足
                        get:
                          operationId: getCategories"""));

            // 確認：読み込みに成功し、操作だけが見つかる
            assertThat(loaded.find("GET", "/api/admin/categories")).hasValue("getCategories");
        }

        @Test
        @DisplayName("パスの下に $ref があると、参照先の操作を読まないので、契約のファイル、パス、キー、原因、直し方を示して読み込みに失敗する")
        void rejectsPathItemRef() {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイル・パス・キー・原因（参照先の操作を読まない）・直し方がある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(pathItem("$ref: './paths/categories.yaml'")))
                    .withMessageContainingAll("admin.yaml", "/categories", "$ref", "参照先の操作を読まない", "パスの下に操作を直接書く");
        }

        @Test
        @DisplayName("パス共通の parameters の中の $ref は、読み込みに失敗しない")
        void acceptsRefInPathParameters() {
            // 操作：パス共通の parameters に $ref を置いた契約を読む
            var loaded = load(pathItem("""
                    parameters:
                          - $ref: '#/components/parameters/CategoryId'
                        get:
                          operationId: getCategories"""));

            // 確認：読み込みに成功し、操作が見つかる
            assertThat(loaded.find("GET", "/api/admin/categories")).hasValue("getCategories");
        }

        @Test
        @DisplayName("操作の応答の $ref は、読み込みに失敗しない")
        void acceptsRefInOperation() {
            // 操作：操作の応答に $ref を置いた契約を読む
            var loaded = load(pathItem("""
                    post:
                          operationId: postCategory
                          responses:
                            '201':
                              $ref: '#/components/responses/Created'"""));

            // 確認：読み込みに成功し、操作が見つかる
            assertThat(loaded.find("POST", "/api/admin/categories")).hasValue("postCategory");
        }
    }

    @Nested
    @DisplayName("重複したキーの検証")
    class DuplicateKeys {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.config.security.OpenApiOperationsTest#duplicateKeySpecs")
        @DisplayName("契約のどこかに重複したキーがあると、後のキーが前のキーを黙って上書きするので、契約のファイルと後のキーの位置を示して読み込みに失敗する")
        void rejectsDuplicateKey(String spec, String position) {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイル・重複したキーであること・後のキーの行と列がある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(spec))
                    .withMessageContainingAll("admin.yaml", "重複したキー", position);
        }
    }

    @Nested
    @DisplayName("YAML として読めない契約の検証")
    class MalformedYaml {

        @Test
        @DisplayName("契約に YAML の書き方の誤りがあると、契約のファイルと誤りの位置を示して読み込みに失敗する")
        void rejectsSyntaxError() {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイル・YAML として読めないこと・誤りの行と列がある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(pathItem("get: operationId: getCategories")))
                    .withMessageContainingAll("admin.yaml", "YAML として読めない", "5 行 21 列");
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "# コメントだけ\n"})
        @DisplayName("契約が空だと、契約のファイルを示して読み込みに失敗する")
        void rejectsEmptyContract(String spec) {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイルと空であることがある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load(spec))
                    .withMessageContainingAll("admin.yaml", "空");
        }

        @Test
        @DisplayName("契約の最上位がキーと値の組でないと、契約のファイルを示して読み込みに失敗する")
        void rejectsNonMappingRoot() {
            // 操作と確認：読み込みに失敗し、知らせに契約のファイルと最上位の形がある
            assertThatIllegalStateException()
                    .isThrownBy(() -> load("- servers\n- paths\n"))
                    .withMessageContainingAll("admin.yaml", "最上位がキーと値の組でない");
        }
    }

    // ============================== テストのデータ ==============================

    /**
     * 重複したキーのある契約
     *
     * @return 契約（名前は例の説明）と、後のキーの位置
     */
    static Stream<Arguments> duplicateKeySpecs() {
        return Stream.of(
                Arguments.of(Named.of("パスの下に get が 2 つ", pathItem("""
                        get:
                              operationId: getCategories
                            get:
                              operationId: getCategories2""")), "7 行 5 列"),
                Arguments.of(Named.of("同じパスが 2 つ", """
                        servers:
                          - url: /api/admin
                        paths:
                          /categories:
                            get:
                              operationId: getCategories
                          /categories:
                            post:
                              operationId: postCategory
                        """), "7 行 3 列"),
                Arguments.of(Named.of("操作の中に x-required-permission が 2 つ", pathItem("""
                        get:
                              operationId: getCategories
                              x-required-permission: product.read
                              x-required-permission: product.write""")), "8 行 7 列"),
                Arguments.of(Named.of("引用符だけが違う同じパス", """
                        servers:
                          - url: /api/admin
                        paths:
                          /carts/{cartId}:
                            get:
                              operationId: getCart
                          '/carts/{cartId}':
                            delete:
                              operationId: deleteCart
                        """), "7 行 3 列"),
                Arguments.of(Named.of("最上位に paths が 2 つ", """
                        servers:
                          - url: /api/admin
                        paths:
                          /categories:
                            get:
                              operationId: getCategories
                        paths:
                          /products:
                            get:
                              operationId: getProducts
                        """), "7 行 1 列"),
                Arguments.of(Named.of("components.schemas の下に同じ名前のスキーマが 2 つ", """
                        servers:
                          - url: /api/admin
                        paths:
                          /categories:
                            get:
                              operationId: getCategories
                        components:
                          schemas:
                            Category:
                              type: object
                            Category:
                              type: string
                        """), "11 行 5 列"));
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
