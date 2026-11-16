package com.ecsite.module.order.application.job;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.checkout.CheckoutService;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.data.OrderTestData.placeOrderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの片付けのジョブの統合テスト
 *
 * <p>期限から保存期間を過ぎた注文手続きの削除、削除した後の注文と注文手続きの扱い、件数の上限、ログを確認する。
 * リポジトリの削除の条件（ちょうどの時刻）は {@code JooqCheckoutRepositoryTest} が確認する。
 *
 * <p>ジョブはテストから呼ぶ。テストごとにロールバックする（片付けはテストのトランザクションに参加する）。
 */
@IntegrationTest
@Transactional
class CheckoutCleanupJobTest {

    // ============================== 定数 ==============================

    /**
     * 注文する時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 注文手続きの期限（引当の期限と同じ 15 分）
     */
    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * 注文した注文手続きの期限から、1 日と 1 時間後（保存期間の 1 日を過ぎた）
     */
    private static final Instant AFTER_RETENTION = NOW.plus(TTL).plus(Duration.ofDays(1)).plus(Duration.ofHours(1));

    /**
     * ほかのテストが使わない古い開始時刻
     *
     * <p>消す条件に当たるのを、そのテストが作った注文手続きだけにする。
     */
    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    /**
     * 件数の上限を小さくした片付けの設定（上限 2 件）
     */
    private static final CheckoutCleanupProperties SMALL_BATCH =
            new CheckoutCleanupProperties(Duration.ofHours(1), Duration.ofDays(1), 2);

    /**
     * ログの確認で消す注文手続きの数（件数の上限の 2 件を超え、分けて消す）
     */
    private static final int DELETED_COUNT = 5;

    // ============================== Bean ==============================

    @Autowired
    CheckoutCleanupJob job;

    @Autowired
    CheckoutService service;

    @Autowired
    CheckoutRepository checkoutRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    /**
     * 在庫 10 の、公開した商品のバリアント ID
     */
    private UUID variantId;

    /**
     * ジョブが出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(CheckoutCleanupJob.class);

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存期間を過ぎた注文手続きの削除")
    class Retention {

        @Test
        @DisplayName("AC-284 期限から保存期間（1 日）より前の注文手続きは消え、その後の注文手続きは残る")
        void deletesCheckoutsExpiredBeforeRetention() {
            // 準備：今始めた注文手続きと、2 時間後に始めた注文手続き
            var old = insertCheckout(NOW);
            var recent = insertCheckout(NOW.plus(Duration.ofHours(2)));

            // 操作：前者の期限から保存期間と 1 時間を過ぎた時刻に、片付ける
            clock.setInstant(AFTER_RETENTION);
            job.cleanUp();

            // 確認：前者だけが消え、後者は残る
            assertThat(remaining(List.of(old, recent))).containsExactly(recent);
        }
    }

    @Nested
    @DisplayName("注文になった注文手続きの削除")
    class OrderedCheckout {

        @Test
        @DisplayName("AC-286 注文になった注文手続きも消える")
        void deletesOrderedCheckout() throws Exception {
            // 準備：注文する
            var orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, 1);
            var checkoutId = dsl.select(SALES_ORDER.CHECKOUT_ID).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                    .fetchOne(SALES_ORDER.CHECKOUT_ID);

            // 操作：保存期間を過ぎた時刻に、片付ける
            clock.setInstant(AFTER_RETENTION);
            job.cleanUp();

            // 確認：注文の注文手続きが消えている
            assertThat(remaining(List.of(checkoutId))).isEmpty();
        }

        @Test
        @DisplayName("AC-286 注文手続きを消しても、店舗の注文の詳細は今どおり表示できる")
        void storeDetailRemainsUsable() throws Exception {
            // 準備：注文してから、注文手続きを片付ける
            var orderId = placeOrderAndCleanUp();

            // 操作と確認：注文の詳細を、受付の状態で表示できる
            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-286 注文手続きを消しても、会員の注文の一覧に今どおり含まれる")
        void storeListRemainsUsable() throws Exception {
            // 準備：注文してから、注文手続きを片付ける
            var orderId = placeOrderAndCleanUp();

            // 操作と確認：会員の注文の一覧に含まれる
            mockMvc.perform(get("/api/store/me/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", hasItem(orderId.toString())));
        }

        @Test
        @DisplayName("AC-286 注文手続きを消しても、管理の注文の詳細と一覧で今どおり表示できる")
        void adminDetailAndListRemainUsable() throws Exception {
            // 準備：注文してから、注文手続きを片付ける
            var orderId = placeOrderAndCleanUp();

            // 操作と確認：管理の注文の詳細を表示できる
            var adminOrder = mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // 操作と確認：管理の注文の一覧に含まれる（他のテストの注文が多いと 1 ページ目に入らないので、自分の注文の注文番号で絞る）
            mockMvc.perform(get("/api/admin/orders").param("orderNumber", JsonPath.<String>read(adminOrder, "$.orderNumber")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", hasItem(orderId.toString())));
        }

        @Test
        @DisplayName("AC-286 注文手続きを消しても、注文のキャンセルの申請は今どおりできる")
        void cancellationRequestRemainsUsable() throws Exception {
            // 準備：注文してから、注文手続きを片付ける
            var orderId = placeOrderAndCleanUp();

            // 操作と確認：キャンセルを申請できる
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("消した注文手続きを使う操作")
    class DeletedCheckout {

        @Test
        @DisplayName("AC-289 消した注文手続きを読む操作は 404 になる")
        void readIsNotFound() throws Exception {
            // 準備：注文手続きに進んでから、片付ける
            var checkoutId = startCheckoutAndCleanUp();

            // 操作と確認：見つからない
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-289 消した注文手続きから注文する操作は 404 になる")
        void placeIsNotFound() throws Exception {
            // 準備：注文手続きに進んでから、片付ける
            var checkoutId = startCheckoutAndCleanUp();

            // 操作と確認：見つからない
            mockMvc.perform(placeOrderRequest(checkoutId, 2100)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("件数の上限")
    class BatchLimit {

        @Test
        @DisplayName("NFR-24 件数の上限を超える注文手続きは、上限ずつ分けて、残りがなくなるまで消す")
        void deletesInBatchesUntilNoneLeft() {
            // 準備：上限（2 件）を超える 5 件の注文手続き
            var checkouts = IntStream.range(0, 5).mapToObj(i -> insertCheckout(NOW)).toList();
            var smallBatch = new CheckoutCleanupJob(service, SMALL_BATCH, clock);

            // 操作：保存期間を過ぎた時刻に、片付ける
            clock.setInstant(AFTER_RETENTION);
            smallBatch.cleanUp();

            // 確認：全てが消えている
            assertThat(remaining(checkouts)).isEmpty();
        }

        @Test
        @DisplayName("NFR-24 1 回の削除は、件数の上限までしか消さない")
        void deletesUpToLimitAtOnce() {
            // 準備：消す条件に当たる 3 件の注文手続き
            var checkouts = IntStream.range(0, 3).mapToObj(i -> insertCheckout(LONG_AGO)).toList();

            // 操作：上限 2 件で 1 回だけ消す
            var deleted = service.deleteExpired(LONG_AGO.plus(TTL).plusSeconds(1), 2);

            // 確認：2 件を消し、1 件が残る
            assertThat(deleted).isEqualTo(2);
            assertThat(remaining(checkouts)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("ログ")
    class Log {

        @Test
        @DisplayName("AC-288 消した件数（分けて消した合計）をログに出す")
        void logsDeletedCount() {
            // 準備：消す条件に当たる 5 件の注文手続きと、上限 2 件のジョブ
            var checkouts = IntStream.range(0, DELETED_COUNT).mapToObj(i -> insertCheckout(LONG_AGO)).toList();
            var smallBatch = new CheckoutCleanupJob(service, SMALL_BATCH, clock);
            var cleanUpAt = LONG_AGO.plus(TTL).plus(SMALL_BATCH.retention()).plusSeconds(1);
            clock.setInstant(cleanUpAt);

            // 準備：消す対象（保持の期間を過ぎた全ての注文手続き）を数える（他のテストがコミットした注文手続きも対象になりうる）
            var targets = dsl.fetchCount(CHECKOUT, CHECKOUT.EXPIRES_AT.lt(cleanUpAt.minus(SMALL_BATCH.retention())));

            // 確認（前提）：このテストの 5 件が対象に入る
            assertThat(targets).isGreaterThanOrEqualTo(DELETED_COUNT);

            // 操作：片付ける
            logs.clear();
            smallBatch.cleanUp();

            // 確認（前提）：5 件の全てが消えている
            assertThat(remaining(checkouts)).isEmpty();

            // 確認：消した合計（数えた対象の数）を INFO のログに 1 回出している
            assertThat(logs.messages(Level.INFO)).containsExactly("期限切れの注文手続きを消した：" + targets + " 件");
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 指定した時刻に始めた注文手続きを、直接保存する（カートと引当は作らない）。
     *
     * @param startedAt 始めた時刻
     * @return 注文手続き ID
     */
    private UUID insertCheckout(Instant startedAt) {
        var checkout = Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, startedAt, TTL, Set.of());
        checkoutRepository.insert(checkout);
        return checkout.id();
    }

    /**
     * 注文して（支払まで済ませて受付にする）、保存期間を過ぎた時刻に片付ける。
     *
     * @return 注文 ID
     */
    private UUID placeOrderAndCleanUp() throws Exception {
        var orderId = new OrderTestData(mockMvc, gateway, applicationContext).placeOrder(variantId, 1);
        clock.setInstant(AFTER_RETENTION);
        job.cleanUp();
        return orderId;
    }

    /**
     * カートから注文手続きに進み、保存期間を過ぎた時刻に片付ける。
     *
     * @return 消した注文手続きの ID
     */
    private UUID startCheckoutAndCleanUp() throws Exception {
        var orders = new OrderTestData(mockMvc);
        var cartId = orders.cart();
        orders.add(cartId, variantId, 1);
        var checkoutId = orders.startCheckout(cartId);
        clock.setInstant(AFTER_RETENTION);
        job.cleanUp();
        return checkoutId;
    }

    /**
     * 指定した注文手続きのうち、残っているものを返す。
     *
     * @param checkoutIds 注文手続き ID
     * @return 残っている注文手続き ID
     */
    private List<UUID> remaining(List<UUID> checkoutIds) {
        return dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.ID.in(checkoutIds)).fetch(CHECKOUT.ID);
    }
}
