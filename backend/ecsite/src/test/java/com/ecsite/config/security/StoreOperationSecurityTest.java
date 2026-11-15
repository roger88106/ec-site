package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 店舗 API の操作ごとの token の要否の検査
 *
 * <p>{@code store.yaml} の {@code security} から読むことを確認する。
 */
class StoreOperationSecurityTest {

    // ============================== 定数 ==============================

    /**
     * token が必要な操作、任意の操作、不要な操作（空の一覧）
     */
    private static final String SPEC = """
            servers:
              - url: /api/store
            paths:
              /me/orders:
                get:
                  security:
                    - memberToken: []
              /carts/{cartId}:
                get:
                  security:
                    - {}
                    - memberToken: []
              /products:
                get:
                  security: []
            """;

    /**
     * {@code security} を書き忘れた操作を含む契約
     */
    private static final String SPEC_WITHOUT_SECURITY = """
            servers:
              - url: /api/store
            paths:
              /me/orders:
                get:
                  security:
                    - memberToken: []
              /products/{productId}:
                get:
                  operationId: getProduct
            """;

    // ============================== テストの対象 ==============================

    /**
     * 操作ごとの token の要否
     */
    private final StoreOperationSecurity security =
            StoreOperationSecurity.load(new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)));

    // ============================== テスト ==============================

    @Nested
    @DisplayName("token の要否")
    class RequiresToken {

        @Test
        @DisplayName("AC-232 security が会員の token だけの操作は、token が必要だ")
        void memberTokenOnlyRequiresToken() {
            // 操作と確認：token が必要
            assertThat(security.requiresToken("GET", "/api/store/me/orders")).hasValue(true);
        }

        @Test
        @DisplayName("AC-233 security に空の要件を含む操作は、token が必要ない")
        void optionalDoesNotRequireToken() {
            // 操作と確認：token が必要ない
            assertThat(security.requiresToken("GET", "/api/store/carts/c1")).hasValue(false);
        }

        @Test
        @DisplayName("AC-233 security が空の一覧の操作は、token が必要ない")
        void publicDoesNotRequireToken() {
            // 操作と確認：token が必要ない
            assertThat(security.requiresToken("GET", "/api/store/products")).hasValue(false);
        }

        @Test
        @DisplayName("契約にない操作は、要否が決まらない（拒む）")
        void unknownOperationIsEmpty() {
            // 操作と確認：要否が空
            assertThat(security.requiresToken("POST", "/api/store/products")).isEmpty();
        }
    }

    @Nested
    @DisplayName("security の書き忘れ")
    class MissingSecurity {

        @Test
        @DisplayName("security のない操作があれば、読み込みに失敗し、操作のメソッドとパスを伝える")
        void operationWithoutSecurityFailsToLoad() {
            // 準備：security を書き忘れた操作を含む契約
            var resource = new ByteArrayResource(SPEC_WITHOUT_SECURITY.getBytes(StandardCharsets.UTF_8));

            // 操作と確認：読み込みに失敗し、知らせにその操作のメソッドとパスがある
            assertThatThrownBy(() -> StoreOperationSecurity.load(resource))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GET /products/{productId}");
        }

        @Test
        @DisplayName("security が値のない（明示の null の）操作があれば、読み込みに失敗し、操作のメソッドとパスを伝える")
        void operationWithNullSecurityFailsToLoad() {
            // 準備：security の値のない操作を含む契約
            var resource = new ByteArrayResource("""
                    servers:
                      - url: /api/store
                    paths:
                      /products/{productId}:
                        get:
                          security:
                    """.getBytes(StandardCharsets.UTF_8));

            // 操作と確認：読み込みに失敗し、知らせにその操作のメソッドとパスがある
            assertThatThrownBy(() -> StoreOperationSecurity.load(resource))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GET /products/{productId}");
        }
    }
}
