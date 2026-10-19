package com.ecsite.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗 API の操作ごとの token の要否を、{@code store.yaml} の {@code security} から読むことの検査。
 */
class StoreOperationSecurityTest {

    /**
     * token が要る操作、任意の操作、要らない操作
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
                  operationId: listProducts
            """;

    /**
     * 操作ごとの token の要否
     */
    private final StoreOperationSecurity security =
            StoreOperationSecurity.load(new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)));

    @Test
    @DisplayName("AC-232 security が会員の token だけの操作は、token が要る")
    void memberTokenOnlyRequiresToken() {
        assertThat(security.requiresToken("GET", "/api/store/me/orders")).hasValue(true);
    }

    @Test
    @DisplayName("AC-233 security に空の要件を含む操作と、security のない操作は、token が要らない")
    void optionalOrPublicDoesNotRequireToken() {
        assertThat(security.requiresToken("GET", "/api/store/carts/c1")).hasValue(false);
        assertThat(security.requiresToken("GET", "/api/store/products")).hasValue(false);
    }

    @Test
    @DisplayName("契約にない操作は、要否が決まらない（拒む）")
    void unknownOperationIsEmpty() {
        assertThat(security.requiresToken("POST", "/api/store/products")).isEmpty();
    }
}
