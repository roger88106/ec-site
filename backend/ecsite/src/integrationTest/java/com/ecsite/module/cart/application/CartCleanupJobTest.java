package com.ecsite.module.cart.application;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.OrderTestData;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.domain.Cart;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.cart.Tables.CART_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの片付けのジョブの統合テスト。<br />
 * ジョブはテストから呼ぶ。テストごとにロールバックする（片付けはテストのトランザクションに参加する）。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CartCleanupJobTest {

    /**
     * カートを変える時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * NOW に変えたカートの期限（30 日後）から 1 時間後
     */
    private static final Instant AFTER_EXPIRY = NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(1));

    @Autowired
    CartCleanupJob job;

    @Autowired
    CartService service;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
    }

    @Test
    @DisplayName("AC-284 最後の変更から 30 日（期限）と保存期間（0）がたったカートは品目と共に消え、それより新しいカートは残る")
    void deletesExpiredCartsWithItems() {
        var old = cartWithItem(NOW);
        var recent = cartWithItem(NOW.plus(Duration.ofDays(2)));

        clock.setInstant(AFTER_EXPIRY);
        job.cleanUp();

        assertThat(remaining(List.of(old, recent))).containsExactly(recent);
        assertThat(dsl.fetchCount(CART_ITEM, CART_ITEM.CART_ID.eq(old))).isZero();
        assertThat(dsl.fetchCount(CART_ITEM, CART_ITEM.CART_ID.eq(recent))).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-289 消したカートを読む・変える・注文手続きを始める操作は 404 になる")
    void deletedCartIsNotFound() throws Exception {
        var cartId = TestJson.createdId(mockMvc.perform(post("/api/store/carts").with(TestTokens.none()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        clock.setInstant(AFTER_EXPIRY);
        job.cleanUp();

        mockMvc.perform(get("/api/store/carts/" + cartId).with(TestTokens.none())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items").with(TestTokens.none())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": 1}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(startCheckoutRequest(cartId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-289 会員のカートを消した後は、会員のカートを求めると再作成される")
    void recreatesMemberCartAfterDeleted() throws Exception {
        var member = TestTokens.member().subject(UUID.randomUUID()).email("cleanup@example.com");
        var before = new OrderTestData(mockMvc).cart(member.bearer());

        clock.setInstant(AFTER_EXPIRY);
        job.cleanUp();

        var after = new OrderTestData(mockMvc).cart(member.bearer());
        assertThat(after).isNotEqualTo(before);
        assertThat(remaining(List.of(before))).isEmpty();
    }

    @Test
    @DisplayName("AC-289 読み込んだ後に片付けで消えたカートを保存すると、予期しないエラーでなく、同時の変更のエラー（409）になる")
    void savingDeletedCartIsConcurrentModification() {
        var cartId = cartWithItem(NOW);
        var loaded = cartRepository.findById(cartId).orElseThrow();

        clock.setInstant(AFTER_EXPIRY);
        job.cleanUp();
        loaded.add(UUID.randomUUID(), 1, AFTER_EXPIRY);

        assertBusinessError(() -> cartRepository.save(loaded), CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("NFR-24 件数の上限を超えるカートは、上限ずつ分けて、残りがなくなるまで消す")
    void deletesInBatchesUntilNoneLeft() {
        var carts = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            carts.add(cartWithItem(NOW));
        }
        var smallBatch = new CartCleanupJob(service, new CartCleanupProperties(Duration.ofHours(1), Duration.ZERO, 2), clock);

        clock.setInstant(AFTER_EXPIRY);
        smallBatch.cleanUp();

        assertThat(remaining(carts)).isEmpty();
    }

    @Test
    @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
    void deletesUpToLimitAtOnce() {
        // ほかのテストが使わない古い時刻にし、消す条件に当たるのを自分の 3 つだけにする
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        var carts = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) {
            carts.add(cartWithItem(longAgo));
        }

        var deleted = service.deleteExpired(longAgo.plus(Duration.ofDays(30)).plusSeconds(1), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(remaining(carts)).hasSize(1);
    }

    @Test
    @DisplayName("AC-288 消した件数（分けて消した合計）をログに出す")
    void logsDeletedCount(CapturedOutput output) {
        // ほかのテストが使わない古い時刻にし、消す条件に当たるのを自分の 5 つだけにする
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        var carts = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            carts.add(cartWithItem(longAgo));
        }
        var smallBatch = new CartCleanupJob(service, new CartCleanupProperties(Duration.ofHours(1), Duration.ZERO, 2), clock);

        clock.setInstant(longAgo.plus(Duration.ofDays(30)).plusSeconds(1));
        smallBatch.cleanUp();

        var deleted = carts.size() - remaining(carts).size();
        assertThat(deleted).isEqualTo(5);
        assertThat(output).contains("期限切れのカートを消した：" + deleted + " 件");
    }

    /**
     * 指定した時刻に 1 品目を入れたカートを、直接保存する（商品カタログと在庫は確認しない）。
     *
     * @return カート ID
     */
    private UUID cartWithItem(Instant changedAt) {
        var cart = Cart.create(UUID.randomUUID(), changedAt);
        assertThat(cartRepository.create(cart)).isTrue();
        cart.add(UUID.randomUUID(), 1, changedAt);
        cartRepository.save(cart);
        return cart.id();
    }

    /**
     * 指定したカートのうち、残っているもの
     */
    private List<UUID> remaining(List<UUID> cartIds) {
        return dsl.select(CART_.ID).from(CART_).where(CART_.ID.in(cartIds)).fetch(CART_.ID);
    }
}
