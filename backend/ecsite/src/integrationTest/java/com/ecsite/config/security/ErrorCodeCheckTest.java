package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 統合テストのエラーの応答の確かめ（{@link ErrorCodeCheck}）のテスト。
 */
class ErrorCodeCheckTest {

    /**
     * テストの対象（クラスパスの契約を読む）
     */
    private final ErrorCodeCheck check = ErrorCodeCheck.load();

    @Test
    @DisplayName("AC-274 操作の一覧にあるコードと、共通のエラーは通す")
    void acceptsListedAndCommonCodes() {
        assertThatCode(() -> check.verify("PUT", "/api/admin/products/00000000-0000-0000-0000-000000000001/publish",
                """
                        {"errorCode": "PRODUCT_HAS_NO_VARIANTS"}""")).doesNotThrowAnyException();
        assertThatCode(() -> check.verify("GET", "/api/admin/products/00000000-0000-0000-0000-000000000001",
                """
                        {"errorCode": "NOT_FOUND"}""")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-274 操作の一覧にも共通のエラーにもないコードを返すと失敗する")
    void rejectsUnlistedCode() {
        assertThatThrownBy(() -> check.verify("PUT", "/api/admin/products/00000000-0000-0000-0000-000000000001/publish",
                """
                        {"errorCode": "OUT_OF_STOCK"}""")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("errorCode のない本文は確かめない")
    void ignoresBodyWithoutErrorCode() {
        assertThatCode(() -> check.verify("GET", "/api/admin/products", "{}")).doesNotThrowAnyException();
    }
}
