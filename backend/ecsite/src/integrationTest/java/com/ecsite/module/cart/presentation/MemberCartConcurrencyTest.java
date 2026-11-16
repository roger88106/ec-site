package com.ecsite.module.cart.presentation;

import com.ecsite.support.async.Concurrently;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.TestJson;
import com.ecsite.support.security.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.jooq.cart.Tables.CART_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員のカートの並行性の受け入れテスト
 *
 * <p>同じ会員の最初のリクエストが同時に来たときに、会員のカートが 1 つだけになることを確認する。
 * 1 つずつのリクエストでの振る舞いは {@code MemberCartApiTest} が確認する。
 *
 * <p>会員ごとの一意制約が働いていることを確認するため、リクエストごとにコミットする。ロールバックしないので、
 * テストごとに新しい会員を使い、その会員のカートだけを確認する。作ったデータは消さない。
 * 同時に始め、上限の時間まで結果を待つのは {@link Concurrently} が行う。
 */
@IntegrationTest
class MemberCartConcurrencyTest {

    // ============================== 定数 ==============================

    /**
     * 会員のカートの API のパス
     */
    private static final String MEMBER_CART = "/api/store/me/cart";

    /**
     * 同時に送るリクエストの数
     */
    private static final int THREADS = 8;

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * DB を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-121 同じ会員の最初のリクエストが同時に来ても、会員のカートは 1 つだけ作られ、全てのリクエストに同じカートが返る")
    void createsOnlyOneCartOnConcurrentFirstRequests() throws Exception {
        // 準備：まだカートのない会員
        var member = TestTokens.member().subject(UUID.randomUUID());

        // 操作：会員のカートを同時に求める
        var cartIds = new HashSet<UUID>(Concurrently.run(THREADS, i -> {
            var body = mockMvc.perform(get(MEMBER_CART).with(member.bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return TestJson.uuid(body, "$.cartId");
        }));

        // 確認：全員が同じカートを受け取り、その会員のカートは 1 つだけ
        assertOnlyOneMemberCart(cartIds);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 返ったカートが 1 つで、その会員のカートが DB に 1 つだけあることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>全てのリクエストに返ったカート ID が 1 種類</li>
     *   <li>そのカートが会員を持つ</li>
     *   <li>その会員のカートが DB に 1 つだけ</li>
     * </ul>
     *
     * @param cartIds 返ったカート ID（重複を除いたもの）
     */
    private void assertOnlyOneMemberCart(Set<UUID> cartIds) {
        assertThat(cartIds).hasSize(1);
        var memberId = dsl.select(CART_.MEMBER_ID).from(CART_).where(CART_.ID.eq(cartIds.iterator().next()))
                .fetchOne(CART_.MEMBER_ID);
        assertThat(memberId).isNotNull();
        assertThat(dsl.fetchCount(CART_, CART_.MEMBER_ID.eq(memberId))).isEqualTo(1);
    }
}
