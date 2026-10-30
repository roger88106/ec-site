package com.ecsite.module.cart.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.cart.Tables.CART_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員のカートの並行性の受け入れテスト（docs/03-機能仕様/cart.md）。<br />
 * 会員ごとの一意制約が働いていることを確認するため、要求ごとにコミットする。ロールバックしないので、
 * テストごとに新しい会員を使い、その会員のカートだけを確認する。作ったデータは消さない。
 */
@IntegrationTest
@AutoConfigureMockMvc
class MemberCartConcurrencyTest {

    private static final String MEMBER_CART = "/api/store/me/cart";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("AC-121 同じ会員の最初のカートの要求が同時に来ても、カートは 1 つだけ作られ、全ての要求が同じカート ID を受け取る")
    void createsOnlyOneCartOnConcurrentFirstRequests() throws Exception {
        var member = TestTokens.member().subject(UUID.randomUUID());
        var threads = 8;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    var body = mockMvc.perform(get(MEMBER_CART).with(member.bearer()))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    return TestJson.uuid(body, "$.cartId");
                }));
            }
            assertThat(ready.await(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var cartIds = new HashSet<UUID>();
            for (var future : futures) {
                cartIds.add(future.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }

            assertThat(cartIds).hasSize(1);
            var memberId = dsl.select(CART_.MEMBER_ID).from(CART_).where(CART_.ID.eq(cartIds.iterator().next()))
                    .fetchOne(CART_.MEMBER_ID);
            assertThat(memberId).isNotNull();
            assertThat(dsl.fetchCount(CART_, CART_.MEMBER_ID.eq(memberId))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
