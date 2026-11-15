package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B 側 API の権限の記載漏れを検出する契約テスト
 *
 * <p>バックエンドのアーキテクチャの「認証・認可」に沿う。{@code admin.yaml} のすべての操作に {@code x-required-permission} があり、{@code resource:action} の形か、
 * ログインしていればよい操作を表す {@code authenticated} であることを確認する。
 * 認証の導入時に、この記載から認可を組み立てる。
 *
 * <p>操作は {@link OpenApiNodes#operations(Map)} でたどる。本番の {@code OpenApiOperations}（{@code config.security} の package-private のクラス）は、
 * リクエストのメソッドとパスから 1 つの操作を探す用途で、契約の全ての操作と権限の記載を一覧にする、この検査の役割とは違うので使わない。
 */
class AdminApiPermissionContractTest {

    // ============================== 定数 ==============================

    /**
     * 権限の拡張項目の名前
     */
    private static final String PERMISSION_KEY = "x-required-permission";

    /**
     * 権限の形
     *
     * <p>resource:action の形で、どちらも英小文字。単語の区切りはハイフン。
     */
    private static final Pattern PERMISSION_FORMAT = Pattern.compile("^[a-z]+(-[a-z]+)*:[a-z]+(-[a-z]+)*$");

    /**
     * 権限ではなく、ログインしていればよい操作を表す値
     */
    private static final String AUTHENTICATED = "authenticated";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("admin.yaml の権限の記載")
    class AdminContract {

        @Test
        @DisplayName("全ての操作に、resource:action の形の権限か authenticated が書かれている")
        void allAdminOperationsDeclarePermission() {
            // 準備：管理 API の契約
            var document = Contracts.openApi("admin.yaml");

            // 操作：全ての操作をたどる
            var operations = OpenApiNodes.operations(document);

            // 確認：たどった操作があり、誤りがない
            assertThat(operations).isNotEmpty();
            assertThat(violations(operations)).isEmpty();
        }

        @Test
        @DisplayName("AC-216 レポートの権限は、レポートの操作ごとに 1 つずつで、条件ごとの操作や権限はない")
        void onePermissionPerReport() {
            // 準備：管理 API の契約
            var document = Contracts.openApi("admin.yaml");

            // 操作：レポートの権限を持つ操作を取り出す
            var reports = OpenApiNodes.operations(document).stream()
                    .filter(operation -> String.valueOf(operation.node().get(PERMISSION_KEY)).startsWith("report-"))
                    .toList();

            // 確認：3 つのレポートの操作に、それぞれ別の権限が 1 つずつ
            assertThat(reports).extracting(operation -> operation.name() + " " + operation.node().get(PERMISSION_KEY))
                    .containsExactlyInAnyOrder(
                            "GET /reports/sales-trend report-sales-trend:read",
                            "GET /reports/variant-sales report-variant-sales:read",
                            "GET /reports/customer-breakdown report-customer:read");
        }
    }

    @Nested
    @DisplayName("検査が誤りを検出できること")
    class Detection {

        @Test
        @DisplayName("権限が書かれていない操作を検出できる")
        void detectsMissingPermission() {
            // 準備：権限のない POST を含む契約
            var document = OpenApiNodes.parse("""
                    paths:
                      /products:
                        get:
                          operationId: listProducts
                          x-required-permission: product:read
                        post:
                          operationId: createProduct
                    """);

            // 操作：権限の書き方を検査する
            var violations = violations(OpenApiNodes.operations(document));

            // 確認：権限のない POST だけを誤りにする
            assertThat(violations).containsExactly("POST /products: x-required-permission がない");
        }

        @Test
        @DisplayName("resource:action の形でない権限を検出できる")
        void detectsMalformedPermission() {
            // 準備：形の正しくない権限 2 つと、authenticated を含む契約
            var document = OpenApiNodes.parse("""
                    paths:
                      /products:
                        get:
                          x-required-permission: Product.Read
                        put:
                          x-required-permission: product
                      /me:
                        get:
                          x-required-permission: authenticated
                    """);

            // 操作：権限の書き方を検査する
            var violations = violations(OpenApiNodes.operations(document));

            // 確認：authenticated は誤りにしない
            assertThat(violations).containsExactly(
                    "GET /products: 形が正しくない（Product.Read）",
                    "PUT /products: 形が正しくない（product）");
        }
    }

    // ============================== 検査 ==============================

    /**
     * 権限の書き方の誤りを列挙する。
     *
     * @param operations 操作の一覧
     * @return 誤りの説明（誤りがなければ空）
     */
    private static List<String> violations(List<OpenApiNodes.Operation> operations) {
        return operations.stream()
                .<String>mapMulti((operation, sink) -> {
                    Object permission = operation.node().get(PERMISSION_KEY);
                    if (permission == null) {
                        sink.accept(operation.name() + ": " + PERMISSION_KEY + " がない");
                    } else if (!AUTHENTICATED.equals(permission)
                            && !PERMISSION_FORMAT.matcher(permission.toString()).matches()) {
                        sink.accept(operation.name() + ": 形が正しくない（" + permission + "）");
                    }
                })
                .toList();
    }
}
