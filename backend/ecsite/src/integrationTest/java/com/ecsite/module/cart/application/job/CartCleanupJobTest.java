package com.ecsite.module.cart.application.job;

import com.ecsite.CapturedLogs;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.cart.application.CartService;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.domain.Cart;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.cart.Tables.CART_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カートの片付けのジョブの統合テスト
 *
 * <p>期限の切れたカートの削除、消したカートを使う操作の結果、件数の上限ごとの削除、件数のログを確認する。
 * 期限の切れたカートの判定そのものは {@code CartTest}、間隔と保存期間の設定は {@code CleanupJobSettingsTest} が確認する。
 *
 * <p>ジョブはテストから呼ぶ。テストごとにロールバックする（片付けはテストのトランザクションに参加する）。
 */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CartCleanupJobTest {

    // ============================== 定数 ==============================

    /**
     * カートを変える時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * NOW に変えたカートの期限（30 日後）から 1 時間後
     */
    private static final Instant AFTER_EXPIRY = NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(1));

    /**
     * NOW の 2 日後（AFTER_EXPIRY では、まだ期限が切れていない）
     */
    private static final Instant TWO_DAYS_LATER = NOW.plus(Duration.ofDays(2));

    /**
     * ほかのテストが使わない古い時刻（消す条件に当たるのを、このテストのカートだけにする）
     */
    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    /**
     * LONG_AGO に変えたカートの期限（30 日後）を過ぎた時刻
     */
    private static final Instant LONG_AGO_EXPIRED = LONG_AGO.plus(Duration.ofDays(30)).plusSeconds(1);

    /**
     * 小さくした件数の上限
     */
    private static final int SMALL_LIMIT = 2;

    // ============================== 部品 ==============================

    /**
     * テストするジョブ（設定の既定の件数の上限）
     */
    @Autowired
    CartCleanupJob job;

    /**
     * カートのサービス
     */
    @Autowired
    CartService service;

    /**
     * カートのリポジトリ
     */
    @Autowired
    CartRepository cartRepository;

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * DB を読む jOOQ
     */
    @Autowired
    DSLContext dsl;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("期限の切れたカートの削除")
    class DeleteExpired {

        @Test
        @DisplayName("AC-284 最後の変更から 30 日（期限）と保存期間（0）がたったカートは消える")
        void deletesExpiredCart() {
            // 準備：NOW に変えたカート
            var old = cartWithItem(NOW);

            // 操作：期限の後に片付ける
            clock.setInstant(AFTER_EXPIRY);
            job.cleanUp();

            // 確認：カートがない
            assertThat(remaining(List.of(old))).isEmpty();
        }

        @Test
        @DisplayName("AC-284 カートを消すと、その品目も消える")
        void deletesItemsWithCart() {
            // 準備：品目を 1 つ入れて NOW に変えたカート
            var old = cartWithItem(NOW);

            // 操作：期限の後に片付ける
            clock.setInstant(AFTER_EXPIRY);
            job.cleanUp();

            // 確認：品目がない
            assertThat(dsl.fetchCount(CART_ITEM, CART_ITEM.CART_ID.eq(old))).isZero();
        }

        @Test
        @DisplayName("AC-284 期限の切れていないカートは、品目と共に残る")
        void keepsCartBeforeExpiry() {
            // 準備：期限の切れたカートと、その 2 日後に変えたカート
            var old = cartWithItem(NOW);
            var recent = cartWithItem(TWO_DAYS_LATER);

            // 操作：古い方の期限の後に片付ける
            clock.setInstant(AFTER_EXPIRY);
            job.cleanUp();

            // 確認：新しい方だけが、品目と共に残る
            assertThat(remaining(List.of(old, recent))).containsExactly(recent);
            assertThat(dsl.fetchCount(CART_ITEM, CART_ITEM.CART_ID.eq(recent))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("消したカートを使う操作")
    class UseDeletedCart {

        @Test
        @DisplayName("AC-289 消したカートを読むと、見つからない")
        void readIsNotFound() throws Exception {
            // 準備：片付けで消したカート
            var cartId = deletedGuestCart();

            // 操作と確認：見つからない
            mockMvc.perform(get("/api/store/carts/" + cartId).with(TestTokens.none())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-289 消したカートに入れると、見つからない")
        void addIsNotFound() throws Exception {
            // 準備：片付けで消したカート
            var cartId = deletedGuestCart();

            // 操作と確認：見つからない
            mockMvc.perform(post("/api/store/carts/" + cartId + "/items").with(TestTokens.none())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"variantId": "%s", "quantity": 1}""".formatted(UUID.randomUUID())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-289 消したカートで注文手続きを始めると、見つからない")
        void startCheckoutIsNotFound() throws Exception {
            // 準備：片付けで消したカート
            var cartId = deletedGuestCart();

            // 操作と確認：見つからない
            mockMvc.perform(startCheckoutRequest(cartId)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-289 会員のカートを消した後は、会員のカートを求めると再作成される")
        void recreatesMemberCartAfterDeleted() throws Exception {
            // 準備：会員のカートを作った後、片付けで消す
            var member = TestTokens.member().subject(UUID.randomUUID()).email("cleanup@example.com");
            var before = new OrderTestData(mockMvc).cart(member.bearer());
            clock.setInstant(AFTER_EXPIRY);
            job.cleanUp();

            // 確認（前提）：前のカートは消えている
            assertThat(remaining(List.of(before))).isEmpty();

            // 操作：もう一度、会員のカートを求める
            var after = new OrderTestData(mockMvc).cart(member.bearer());

            // 確認：別のカートが作られる
            assertThat(after).isNotEqualTo(before);
        }

        @Test
        @DisplayName("AC-289 読み込んだ後に片付けで消えたカートを保存すると、予期しないエラーでなく、同時の変更のエラーになる")
        void savingDeletedCartIsConcurrentModification() {
            // 準備：読み込んだ後に片付けで消し、読み込んだ方に品目を入れる
            var cartId = cartWithItem(NOW);
            var loaded = cartRepository.findById(cartId).orElseThrow();
            clock.setInstant(AFTER_EXPIRY);
            job.cleanUp();
            loaded.add(UUID.randomUUID(), 1, AFTER_EXPIRY);

            // 操作と確認：同時の変更のエラー
            assertBusinessError(() -> cartRepository.save(loaded), CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    @Nested
    @DisplayName("件数の上限")
    class Limit {

        @Test
        @DisplayName("NFR-24 件数の上限を超えるカートは、上限ずつ分けて、残りがなくなるまで消す")
        void deletesInBatchesUntilNoneLeft() {
            // 準備：期限の切れる 5 つのカートと、件数の上限を 2 にしたジョブ
            var carts = cartsWithItem(5, NOW);
            var smallBatch = jobWithLimit(SMALL_LIMIT);

            // 操作：期限の後に片付ける
            clock.setInstant(AFTER_EXPIRY);
            smallBatch.cleanUp();

            // 確認：5 つとも消えている
            assertThat(remaining(carts)).isEmpty();
        }

        @Test
        @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
        void deletesUpToLimitAtOnce() {
            // 準備：ほかのテストが使わない古い時刻に変えた 3 つのカート
            var carts = cartsWithItem(3, LONG_AGO);

            // 操作：上限を 2 にして 1 回消す
            var deleted = service.deleteExpired(LONG_AGO_EXPIRED, SMALL_LIMIT);

            // 確認：2 つだけ消え、1 つ残る
            assertThat(deleted).isEqualTo(SMALL_LIMIT);
            assertThat(remaining(carts)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("ログ")
    class Logging {

        @Test
        @DisplayName("AC-288 消した件数（分けて消した合計）をログに出す")
        void logsDeletedCount(CapturedOutput output) {
            // 準備：ほかのテストが使わない古い時刻に変えた 5 つのカートと、件数の上限を 2 にしたジョブ
            var carts = cartsWithItem(5, LONG_AGO);
            var smallBatch = jobWithLimit(SMALL_LIMIT);
            clock.setInstant(LONG_AGO_EXPIRED);
            var logStart = CapturedLogs.mark(output);

            // 操作：片付ける
            smallBatch.cleanUp();

            // 確認（前提）：5 つとも消えている
            assertThat(remaining(carts)).isEmpty();

            // 確認：合計の 5 件をログに出している
            assertThat(CapturedLogs.since(output, logStart)).contains("期限切れのカートを消した：5 件");
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * ログインせずにカートを作り、期限の後に片付けて消す。
     *
     * @return 消したカートの ID
     */
    private UUID deletedGuestCart() throws Exception {
        var cartId = TestJson.createdId(mockMvc.perform(post("/api/store/carts").with(TestTokens.none()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        clock.setInstant(AFTER_EXPIRY);
        job.cleanUp();
        return cartId;
    }

    /**
     * 件数の上限を変えたジョブを作る（保存期間は 0）。
     *
     * @param limit 1 回に消す件数の上限
     * @return ジョブ
     */
    private CartCleanupJob jobWithLimit(int limit) {
        return new CartCleanupJob(service, new CartCleanupProperties(Duration.ofHours(1), Duration.ZERO, limit), clock);
    }

    /**
     * 指定した時刻に 1 品目を入れたカートを、指定した数だけ作る。
     *
     * @param count     カートの数
     * @param changedAt 最後に変えた時刻
     * @return カート ID
     */
    private List<UUID> cartsWithItem(int count, Instant changedAt) {
        return IntStream.range(0, count).mapToObj(i -> cartWithItem(changedAt)).toList();
    }

    /**
     * 指定した時刻に 1 品目を入れたカートを、直接保存する（商品カタログと在庫は確認しない）。
     *
     * @param changedAt 最後に変えた時刻
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
     * 指定したカートのうち、残っているものを返す。
     *
     * @param cartIds カート ID
     * @return 残っているカートの ID
     */
    private List<UUID> remaining(List<UUID> cartIds) {
        return dsl.select(CART_.ID).from(CART_).where(CART_.ID.in(cartIds)).fetch(CART_.ID);
    }
}
