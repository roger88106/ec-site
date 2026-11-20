package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * リクエストのパスの取り出しの単体テスト
 *
 * <p>認可は、取り出したパスを契約のパスと照合する。
 */
class RequestPathsTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-8 コンテキストパスがなければ、リクエストの URI のパスをそのまま返す")
    void returnsRequestUriWithoutContextPath() {
        // 準備：コンテキストパスのないリクエスト
        var request = new MockHttpServletRequest("GET", "/api/store/products");

        // 操作と確認：URI のパスのまま
        assertThat(RequestPaths.pathOf(request)).isEqualTo("/api/store/products");
    }

    @Test
    @DisplayName("DC-477-8 コンテキストパスがあれば、それを除いたパスを返す")
    void removesContextPath() {
        // 準備：コンテキストパスの下のリクエスト
        var request = new MockHttpServletRequest("GET", "/shop/api/store/products");
        request.setContextPath("/shop");

        // 操作と確認：コンテキストパスを除いたパス
        assertThat(RequestPaths.pathOf(request)).isEqualTo("/api/store/products");
    }

    @Test
    @DisplayName("DC-477-8 符号化した文字は、復号せずに返す")
    void keepsEncodedCharacters() {
        // 準備：符号化したスラッシュと点を含むリクエスト
        var request = new MockHttpServletRequest("GET", "/api/store/me%2Fcart/%2e%2e");

        // 操作と確認：符号化したまま
        assertThat(RequestPaths.pathOf(request)).isEqualTo("/api/store/me%2Fcart/%2e%2e");
    }
}
