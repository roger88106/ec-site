package com.ecsite.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B 側 API の権限の記載漏れを検出する契約テスト（バックエンドのアーキテクチャ 第 7 節）。<br />
 * {@code admin.yaml} のすべての操作に {@code x-required-permission} があり、{@code resource:action} の形か、
 * ログインしていればよい操作を表す {@code authenticated} であることを確認する。
 * 認証の導入時に、この記載から認可を組み立てる。
 */
class AdminApiPermissionContractTest {

    /**
     * 権限の拡張項目の名前
     */
    private static final String PERMISSION_KEY = "x-required-permission";

    /**
     * 権限の形：resource:action（どちらも英小文字。単語の区切りはハイフン）
     */
    private static final Pattern PERMISSION_FORMAT = Pattern.compile("^[a-z]+(-[a-z]+)*:[a-z]+(-[a-z]+)*$");

    /**
     * 権限ではなく、ログインしていればよい操作を表す値
     */
    private static final String AUTHENTICATED = "authenticated";

    /**
     * OpenAPI で操作を表す HTTP メソッド
     */
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

    @Test
    @DisplayName("admin.yaml のすべての操作に、resource:action の形の権限か authenticated が書かれている")
    void allAdminOperationsDeclarePermission() throws IOException {
        var spec = Files.readString(Contracts.openApiDir().resolve("admin.yaml"));

        var operations = listOperations(spec);

        assertThat(operations).isNotEmpty();
        assertThat(violations(operations)).isEmpty();
    }

    @Test
    @DisplayName("AC-216 レポートの権限は、レポートの操作ごとに 1 つずつで、条件ごとの操作や権限はない")
    void onePermissionPerReport() throws IOException {
        var spec = Files.readString(Contracts.openApiDir().resolve("admin.yaml"));

        var reports = listOperations(spec).stream()
                .filter(operation -> String.valueOf(operation.permission()).startsWith("report-"))
                .toList();

        assertThat(reports).extracting(operation -> operation.method() + " " + operation.path() + " " + operation.permission())
                .containsExactlyInAnyOrder(
                        "GET /reports/sales-trend report-sales-trend:read",
                        "GET /reports/variant-sales report-variant-sales:read",
                        "GET /reports/customer-breakdown report-customer:read");
    }

    @Test
    @DisplayName("権限が書かれていない操作を検出できる")
    void detectsMissingPermission() {
        var spec = """
                paths:
                  /products:
                    get:
                      operationId: listProducts
                      x-required-permission: product:read
                    post:
                      operationId: createProduct
                """;

        assertThat(violations(listOperations(spec)))
                .containsExactly("POST /products: x-required-permission がない");
    }

    @Test
    @DisplayName("resource:action の形でない権限を検出できる")
    void detectsMalformedPermission() {
        var spec = """
                paths:
                  /products:
                    get:
                      x-required-permission: Product.Read
                    put:
                      x-required-permission: product
                  /me:
                    get:
                      x-required-permission: authenticated
                """;

        assertThat(violations(listOperations(spec))).containsExactly(
                "GET /products: 形が正しくない（Product.Read）",
                "PUT /products: 形が正しくない（product）");
    }

    // ============================== 検査 ==============================

    /**
     * 操作
     *
     * @param method     HTTP メソッド（大文字）
     * @param path       パス
     * @param permission 権限（書かれていなければ null）
     */
    private record Operation(String method, String path, Object permission) {
    }

    /**
     * OpenAPI の文書から、すべての操作を取り出す。
     * 本番の {@code OpenApiOperations}（{@code config.security} の package-private のクラス）は、リクエストのメソッドとパスから
     * 1 つの操作を探す用途で、契約の全ての操作と権限の記載を一覧にする、この検査の役割とは違う。そのため使わず、契約を直接たどる。
     *
     * @param spec OpenAPI の文書（読み込み済み）
     * @return 操作の一覧
     */
    @SuppressWarnings("unchecked")
    private static List<Operation> listOperations(String spec) {
        Map<String, Object> root = new Yaml().load(spec);
        var paths = (Map<String, Map<String, Object>>) root.get("paths");

        var operations = new ArrayList<Operation>();
        paths.forEach((path, item) -> item.forEach((key, value) -> {
            // パス共通の parameters などは操作ではない
            if (HTTP_METHODS.contains(key)) {
                var operation = (Map<String, Object>) value;
                operations.add(new Operation(key.toUpperCase(Locale.ROOT), path, operation.get(PERMISSION_KEY)));
            }
        }));
        return operations;
    }

    /**
     * 権限の書き方の誤りを列挙する。
     *
     * @param operations 操作の一覧
     * @return 誤りの説明（誤りがなければ空）
     */
    private static List<String> violations(List<Operation> operations) {
        return operations.stream()
                .<String>mapMulti((op, sink) -> {
                    var name = op.method() + " " + op.path();
                    if (op.permission() == null) {
                        sink.accept(name + ": " + PERMISSION_KEY + " がない");
                    } else if (!AUTHENTICATED.equals(op.permission())
                            && !PERMISSION_FORMAT.matcher(op.permission().toString()).matches()) {
                        sink.accept(name + ": 形が正しくない（" + op.permission() + "）");
                    }
                })
                .toList();
    }
}
