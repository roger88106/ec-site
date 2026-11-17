package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * エラーの応答の確認（{@link ErrorCodeCheck}）のテスト
 */
class ErrorCodeCheckTest {

    // ============================== 定数 ==============================

    /**
     * 商品の公開の操作のパス（契約の操作の一覧に {@code PRODUCT_HAS_NO_VARIANTS} があり、{@code OUT_OF_STOCK} がない）
     */
    private static final String PUBLISH_PRODUCT = "/api/admin/products/00000000-0000-0000-0000-000000000001/publish";

    // ============================== テストの対象 ==============================

    /**
     * テストの対象（クラスパスの契約を読む）
     */
    private final ErrorCodeCheck check = ErrorCodeCheck.load();

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-274 操作の一覧にあるコードは通す")
    void acceptsListedCode() {
        // 操作と確認：一覧にあるコードを返しても、失敗しない
        assertThatCode(() -> check.verify("PUT", PUBLISH_PRODUCT, """
                {"errorCode": "PRODUCT_HAS_NO_VARIANTS"}""")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-274 共通のエラーのコードは、操作の一覧になくても通す")
    void acceptsCommonCode() {
        // 操作と確認：商品の取得の一覧にない共通のエラーを返しても、失敗しない
        assertThatCode(() -> check.verify("GET", "/api/admin/products/00000000-0000-0000-0000-000000000001", """
                {"errorCode": "NOT_FOUND"}""")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-274 操作の一覧にも共通のエラーにもないコードを返すと失敗する")
    void rejectsUnlistedCode() {
        // 操作と確認：一覧にないコードを返すと、そのコードを示して失敗する
        assertThatThrownBy(() -> check.verify("PUT", PUBLISH_PRODUCT, """
                {"errorCode": "OUT_OF_STOCK"}""")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("errorCode のない本文は確認しない")
    void ignoresBodyWithoutErrorCode() {
        // 操作と確認：errorCode のない本文は、失敗しない
        assertThatCode(() -> check.verify("GET", "/api/admin/products", "{}")).doesNotThrowAnyException();
    }
}
