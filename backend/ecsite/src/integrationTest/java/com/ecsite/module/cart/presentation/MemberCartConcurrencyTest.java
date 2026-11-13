package com.ecsite.module.cart.presentation;

import com.ecsite.Concurrently;
import com.ecsite.IntegrationTest;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.UUID;

import static com.ecsite.jooq.cart.Tables.CART_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員のカートの並行性の受け入れテスト
 *
 * <p>会員ごとの一意制約が働いていることを確認するため、リクエストごとにコミットする。ロールバックしないので、
 * テストごとに新しい会員を使い、その会員のカートだけを確認する。作ったデータは消さない。
 */
@IntegrationTest
class MemberCartConcurrencyTest {

    private static final String MEMBER_CART = "/api/store/me/cart";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("AC-121 同じ会員の最初のカートのリクエストが同時に来ても、カートは 1 つだけ作られ、全てのリクエストが同じカート ID を受け取る")
    void createsOnlyOneCartOnConcurrentFirstRequests() throws Exception {
        var member = TestTokens.member().subject(UUID.randomUUID());
        var threads = 8;
        var cartIds = new HashSet<UUID>(Concurrently.run(threads, i -> {
            var body = mockMvc.perform(get(MEMBER_CART).with(member.bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return TestJson.uuid(body, "$.cartId");
        }));

        assertThat(cartIds).hasSize(1);
        var memberId = dsl.select(CART_.MEMBER_ID).from(CART_).where(CART_.ID.eq(cartIds.iterator().next()))
                .fetchOne(CART_.MEMBER_ID);
        assertThat(memberId).isNotNull();
        assertThat(dsl.fetchCount(CART_, CART_.MEMBER_ID.eq(memberId))).isEqualTo(1);
    }
}
