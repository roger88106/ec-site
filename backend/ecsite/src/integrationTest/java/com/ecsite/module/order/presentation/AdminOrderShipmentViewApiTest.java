package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.data.ShippingTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.module.order.presentation.AdminOrderLists.idsOf;
import static com.ecsite.module.order.presentation.AdminOrderLists.itemOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の注文の一覧・詳細の、出荷と与信期限の表示と絞り込みの受け入れテスト
 *
 * <p>受付・出荷済み・返送の 3 件の注文で、出荷の項目、与信期限が近い印、返金待ち、出荷と与信期限と返送の絞り込みを確認する。
 * 一覧の並びと、注文番号・状態・注文日の絞り込みは {@code AdminOrderApiTest} が確認する。
 *
 * <p>与信期限は 7 日、「期限が近い」は期限の 2 日前から（設定の既定）。一覧は他のテストの注文も含むので、自分の注文を ID で選んで確認する。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class AdminOrderShipmentViewApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/orders";

    /**
     * 注文・与信・出荷の時刻
     *
     * <p>日本時間の 2026-11-20 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    /**
     * 与信期限（与信の時刻から 7 日）
     *
     * <p>日本時間の 2026-11-27 00:00。
     */
    private static final Instant AUTHORIZATION_DEADLINE = NOW.plus(Duration.ofDays(7));

    /**
     * 出荷済みの注文の送り状番号
     */
    private static final String SHIPPED_TRACKING_NUMBER = "111111111111";

    /**
     * 返送の注文の送り状番号
     */
    private static final String RETURNED_TRACKING_NUMBER = "222222222222";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

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
     * 注文のテストデータ
     */
    private OrderTestData orders;

    /**
     * 注文の一覧を全ページから集める補助
     */
    private AdminOrderLists lists;

    /**
     * 出荷を作る補助
     */
    private ShippingTestData shipping;

    /**
     * 受付のまま（出荷待ち）の注文
     */
    private UUID accepted;

    /**
     * 出荷済み（配送状態も出荷済み）の注文
     */
    private UUID shipped;

    /**
     * 出荷済みで、配送状態が返送の注文
     */
    private UUID returned;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", "10"), "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 10);
        // 商品の公開で時計が進むので、注文と与信の時刻をそろえる
        clock.setInstant(NOW);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        lists = new AdminOrderLists(mockMvc);
        shipping = new ShippingTestData(mockMvc, mockCarrier);
        accepted = orders.placeOrder(variantId, 1, "受付 太郎");
        shipped = orders.placeOrder(variantId, 1, "出荷 花子");
        returned = orders.placeOrder(variantId, 1, "返送 次郎");
        shipping.ship(shipped, Carrier.YAMATO, SHIPPED_TRACKING_NUMBER);
        shipping.ship(returned, Carrier.YAMATO, RETURNED_TRACKING_NUMBER);
        shipping.notifyStatus(Carrier.YAMATO, RETURNED_TRACKING_NUMBER, "返品");
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文の詳細の出荷")
    class DetailShipment {

        @Test
        @DisplayName("AC-181 出荷の配送業者、送り状番号、配送状態、出荷日時を返す")
        void showsShipment() throws Exception {
            // 操作と確認：返送の注文の出荷の全ての項目
            detail(returned)
                    .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value(RETURNED_TRACKING_NUMBER))
                    .andExpect(jsonPath("$.shipment.status").value("RETURNED"))
                    .andExpect(jsonPath("$.shipment.shippedAt").value(NOW.toString()));
        }

        @Test
        @DisplayName("AC-181 出荷していない注文は、出荷を返さない")
        void omitsShipmentWhenNotShipped() throws Exception {
            // 操作と確認：受付の注文に出荷の項目がない
            detail(accepted).andExpect(jsonPath("$.shipment").doesNotExist());
        }

        @Test
        @DisplayName("AC-194 返送の注文をキャンセルすると、返金が済むまで支払は返金待ちを示す")
        void showsRefundPending() throws Exception {
            // 準備：返送の注文を、在庫に戻してキャンセルする
            mockMvc.perform(put(BASE + "/" + returned + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "返送された", "restock": true}"""))
                    .andExpect(status().isNoContent());

            // 操作と確認：支払は返金待ち
            detail(returned).andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
        }
    }

    @Nested
    @DisplayName("注文の詳細の与信期限")
    class DetailAuthorizationDeadline {

        @Test
        @DisplayName("AC-186 与信期限を返し、期限の 2 日前より前は「期限が近い」を示さない")
        void showsDeadlineNotExpiringYet() throws Exception {
            // 操作と確認：与信の時刻から 7 日後の期限で、まだ近くない
            detail(accepted)
                    .andExpect(jsonPath("$.payment.authorizationDeadline").value(AUTHORIZATION_DEADLINE.toString()))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));
        }

        @Test
        @DisplayName("AC-186 期限の 2 日前になると「期限が近い」を示す")
        void expiresTwoDaysBefore() throws Exception {
            // 準備：期限のちょうど 2 日前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(2)));

            // 操作と確認：近いを示す
            detail(accepted).andExpect(jsonPath("$.payment.authorizationExpiring").value(true));
        }

        @Test
        @DisplayName("AC-186 期限を過ぎても「期限が近い」を示したまま")
        void keepsExpiringAfterDeadline() throws Exception {
            // 準備：期限の 1 秒後にする
            clock.setInstant(AUTHORIZATION_DEADLINE.plusSeconds(1));

            // 操作と確認：近いを示したまま
            detail(accepted).andExpect(jsonPath("$.payment.authorizationExpiring").value(true));
        }

        @Test
        @DisplayName("AC-186 売上を確定した注文は、期限が近くならない")
        void capturedIsNotExpiring() throws Exception {
            // 準備：期限の 1 秒前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minusSeconds(1));

            // 操作と確認：出荷済みの注文は売上確定済みで、近いを示さない
            detail(shipped)
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));
        }

        @Test
        @DisplayName("AC-186、188 与信期限切れでキャンセルした注文の詳細は、「期限が近い」を示さない")
        void cancelledIsNotExpiringInDetail() throws Exception {
            // 準備：期限を過ぎて出荷に失敗した受付の注文を、キャンセルする
            cancelAfterExpiredCapture();

            // 操作と確認：支払は与信期限切れで、近いを示さない
            detail(accepted)
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZATION_EXPIRED"))
                    .andExpect(jsonPath("$.payment.authorizationExpiring").value(false));
        }

        @Test
        @DisplayName("AC-187、188 与信期限切れでキャンセルした注文は、「期限が近い」の絞り込みに出ない")
        void cancelledIsNotExpiringInList() throws Exception {
            // 準備：期限を過ぎて出荷に失敗した受付の注文を、キャンセルする
            cancelAfterExpiredCapture();

            // 操作：期限が近い注文で絞り込む
            var expiring = lists.listAll("authorizationExpiring", "true");

            // 確認：自分の注文はどれも出ず、全ての行が期限が近い
            assertThat(idsOf(expiring, accepted, shipped, returned)).isEmpty();
            assertThat(expiring).allSatisfy(item -> assertThat(item.get("authorizationExpiring")).isEqualTo(true));
        }
    }

    @Nested
    @DisplayName("注文一覧の項目")
    class ListColumns {

        @Test
        @DisplayName("AC-189 各行に配送状態を返す。出荷していなければ null")
        void showsShipmentStatus() throws Exception {
            // 操作：一覧を取得する
            var items = lists.listAll();

            // 確認：返送・出荷済み・出荷なしの配送状態
            assertThat(itemOf(items, returned).get("shipmentStatus")).isEqualTo("RETURNED");
            assertThat(itemOf(items, shipped).get("shipmentStatus")).isEqualTo("SHIPPED");
            assertThat(itemOf(items, accepted).get("shipmentStatus")).isNull();
        }

        @Test
        @DisplayName("AC-186 各行に「与信期限が近い」を返し、受付の注文だけに示す")
        void showsAuthorizationExpiring() throws Exception {
            // 準備：期限の 1 日前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));

            // 操作：一覧を取得する
            var items = lists.listAll();

            // 確認：受付の注文は近いを示し、売上を確定した出荷済みの注文は示さない
            assertThat(itemOf(items, accepted).get("authorizationExpiring")).isEqualTo(true);
            assertThat(itemOf(items, shipped).get("authorizationExpiring")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("注文一覧の絞り込み")
    class ListFilters {

        @Test
        @DisplayName("AC-182 状態「出荷済み」で絞り込むと、出荷済みの注文が並ぶ")
        void filtersByShipped() throws Exception {
            // 操作：出荷済みで絞り込む
            var items = lists.listAll("status", "SHIPPED");

            // 確認：出荷済みと返送の注文が出て、全ての行が出荷済み
            assertThat(idsOf(items, accepted, shipped, returned)).containsExactlyInAnyOrder(shipped.toString(), returned.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("status")).isEqualTo("SHIPPED"));
        }

        @Test
        @DisplayName("AC-182 状態「受付」で絞り込むと、出荷待ちの注文が並ぶ")
        void filtersByAccepted() throws Exception {
            // 操作：受付で絞り込む
            var items = lists.listAll("status", "ACCEPTED");

            // 確認：出荷していない注文だけが出て、全ての行が受付
            assertThat(idsOf(items, accepted, shipped, returned)).containsExactly(accepted.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("status")).isEqualTo("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-187 与信期限が近い注文で絞り込むと、期限の 2 日前より前の注文は出ない")
        void excludesNotYetExpiring() throws Exception {
            // 操作：期限が近い注文で絞り込む
            var items = lists.listAll("authorizationExpiring", "true");

            // 確認：自分の注文はどれも出ず、全ての行が期限が近い
            assertThat(idsOf(items, accepted, shipped, returned)).isEmpty();
            assertThat(items).allSatisfy(item -> assertThat(item.get("authorizationExpiring")).isEqualTo(true));
        }

        @Test
        @DisplayName("AC-187 与信期限が近い注文で絞り込める")
        void filtersByAuthorizationExpiring() throws Exception {
            // 準備：期限の 1 日前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));

            // 操作：期限が近い注文で絞り込む
            var items = lists.listAll("authorizationExpiring", "true");

            // 確認：受付の注文だけが出て、全ての行が期限が近い
            assertThat(idsOf(items, accepted, shipped, returned)).containsExactly(accepted.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("authorizationExpiring")).isEqualTo(true));
        }

        @Test
        @DisplayName("AC-187 与信期限が近い注文の絞り込みは、状態の絞り込みと組み合わせられる")
        void combinesAuthorizationExpiringWithStatus() throws Exception {
            // 準備：期限の 1 日前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));

            // 操作：期限が近い、かつキャンセルで絞り込む
            var items = lists.listAll("authorizationExpiring", "true", "status", "CANCELLED");

            // 確認：期限が近い受付の注文は出ず、全ての行が両方の条件を満たす
            assertThat(idsOf(items, accepted, shipped, returned)).isEmpty();
            assertThat(items).allSatisfy(item -> {
                assertThat(item.get("authorizationExpiring")).isEqualTo(true);
                assertThat(item.get("status")).isEqualTo("CANCELLED");
            });
        }

        @Test
        @DisplayName("AC-187 与信期限が近いの条件に false を指定すると、絞り込まない")
        void doesNotFilterWithFalse() throws Exception {
            // 準備：期限の 1 日前にする
            clock.setInstant(AUTHORIZATION_DEADLINE.minus(Duration.ofDays(1)));

            // 操作：false で絞り込む
            var items = lists.listAll("authorizationExpiring", "false");

            // 確認：自分の 3 件が全て出る
            assertThat(idsOf(items, accepted, shipped, returned))
                    .containsExactlyInAnyOrder(accepted.toString(), shipped.toString(), returned.toString());
        }

        @Test
        @DisplayName("AC-189 配送状態「返送」の注文で絞り込める")
        void filtersByReturned() throws Exception {
            // 操作：返送で絞り込む
            var items = lists.listAll("returned", "true");

            // 確認：返送の注文だけが出て、全ての行が返送
            assertThat(idsOf(items, accepted, shipped, returned)).containsExactly(returned.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("shipmentStatus")).isEqualTo("RETURNED"));
        }

        @Test
        @DisplayName("AC-189 返送の絞り込みは、状態の絞り込みと組み合わせられる")
        void combinesReturnedWithStatus() throws Exception {
            // 操作：返送、かつ受付で絞り込む
            var items = lists.listAll("returned", "true", "status", "ACCEPTED");

            // 確認：自分の注文はどれも出ず、全ての行が両方の条件を満たす
            assertThat(idsOf(items, accepted, shipped, returned)).isEmpty();
            assertThat(items).allSatisfy(item -> {
                assertThat(item.get("shipmentStatus")).isEqualTo("RETURNED");
                assertThat(item.get("status")).isEqualTo("ACCEPTED");
            });
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文の詳細を取得する。
     *
     * @param orderId 注文 ID
     * @return 応答
     */
    private ResultActions detail(UUID orderId) throws Exception {
        return mockMvc.perform(get(BASE + "/" + orderId));
    }

    /**
     * 与信期限を過ぎてから受付の注文の出荷を登録して失敗させ、運営者がキャンセルする。
     */
    private void cancelAfterExpiredCapture() throws Exception {
        clock.setInstant(AUTHORIZATION_DEADLINE.plusSeconds(1));
        gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(accepted)));
        mockMvc.perform(post(BASE + "/" + accepted + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"carrier": "YAMATO", "trackingNumber": "333333333333"}"""))
                .andExpect(status().isConflict());
        mockMvc.perform(put(BASE + "/" + accepted + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason": "与信期限切れ"}"""))
                .andExpect(status().isNoContent());
    }
}
