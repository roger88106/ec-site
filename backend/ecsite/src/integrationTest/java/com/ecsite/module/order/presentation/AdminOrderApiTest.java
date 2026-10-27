package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.module.order.presentation.AdminOrderLists.idsOf;
import static com.ecsite.module.order.presentation.AdminOrderLists.itemOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の注文の一覧・詳細の受け入れテスト。<br />
 * 注文は C 側の API で作る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class AdminOrderApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/orders";

    /**
     * 日本時間
     */
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /**
     * 2026-12-08 23:59（日本時間）。日付をまたぐ境界の確認に使う
     */
    private static final Instant DEC_8_LATE = Instant.parse("2026-12-08T14:59:00Z");

    /**
     * 2026-12-09 00:00（日本時間）
     */
    private static final Instant DEC_9 = Instant.parse("2026-12-08T15:00:00Z");

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
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * 注文の一覧を全ページから集める補助
     */
    private AdminOrderLists lists;

    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(DEC_8_LATE.minusSeconds(3600));
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(data.taxCategory("標準", "10"), "T シャツ", 2700, "色").variant("白").publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 50);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        lists = new AdminOrderLists(mockMvc);
    }

    @Nested
    @DisplayName("AC-97、98、99、106 注文一覧")
    class ListOrders {

        @Test
        @DisplayName("AC-97、106 注文日時の新しい順に、注文番号・注文日時・氏名・総額・状態を返す。キャンセル済みも含む")
        void listsNewestFirst() throws Exception {
            clock.setInstant(DEC_8_LATE);
            var older = orders.placeOrder(variantId, 1, "佐藤 花子");
            clock.setInstant(DEC_9);
            var newer = orders.placeOrder(variantId, 2, "山田 太郎");
            cancel(older);

            var items = lists.listAll();

            assertThat(idsOf(items, older, newer)).containsExactly(newer.toString(), older.toString());
            var newerItem = itemOf(items, newer);
            assertThat(newerItem.get("customerName")).isEqualTo("山田 太郎");
            assertThat(newerItem.get("total")).isEqualTo(2970 * 2 + 1000);
            assertThat(newerItem.get("status")).isEqualTo("ACCEPTED");
            assertThat(newerItem.get("orderNumber")).isEqualTo(orderNumber(newer));
            assertThat(newerItem.get("orderedAt")).isNotNull();
            assertThat(itemOf(items, older).get("status")).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("AC-98 注文番号の前方一致、状態、注文日の範囲（日本時間）で絞り込める")
        void filters() throws Exception {
            clock.setInstant(DEC_8_LATE);
            var dec8 = orders.placeOrder(variantId, 1);
            clock.setInstant(DEC_9);
            var dec9 = orders.placeOrder(variantId, 1);
            cancel(dec9);

            var byDate = lists.listAll("orderNumber", "20261209");
            assertThat(idsOf(byDate, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(byDate).allSatisfy(item -> assertThat((String) item.get("orderNumber")).startsWith("20261209"));

            var byNumber = lists.listAll("orderNumber", orderNumber(dec8));
            assertThat(idsOf(byNumber, dec8, dec9)).containsExactly(dec8.toString());
            assertThat(byNumber).allSatisfy(item -> assertThat(item.get("orderNumber")).isEqualTo(orderNumber(dec8)));

            var cancelled = lists.listAll("status", "CANCELLED");
            assertThat(idsOf(cancelled, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(cancelled).allSatisfy(item -> assertThat(item.get("status")).isEqualTo("CANCELLED"));

            var from = lists.listAll("orderedFrom", "2026-12-09");
            assertThat(idsOf(from, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(from).allSatisfy(item -> assertThat(orderedDate(item)).isAfterOrEqualTo(LocalDate.of(2026, 12, 9)));

            var to = lists.listAll("orderedTo", "2026-12-08");
            assertThat(idsOf(to, dec8, dec9)).containsExactly(dec8.toString());
            assertThat(to).allSatisfy(item -> assertThat(orderedDate(item)).isBeforeOrEqualTo(LocalDate.of(2026, 12, 8)));

            var range = lists.listAll("orderedFrom", "2026-12-08", "orderedTo", "2026-12-09");
            assertThat(idsOf(range, dec8, dec9)).containsExactly(dec9.toString(), dec8.toString());
            assertThat(range).allSatisfy(item -> assertThat(orderedDate(item))
                    .isBetween(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9)));
        }

        @Test
        @DisplayName("AC-99 ページ単位で取得できる")
        void pages() throws Exception {
            for (int i = 0; i < 3; i++) {
                orders.placeOrder(variantId, 1);
            }
            var all = lists.listAll();

            var body = mockMvc.perform(get(BASE).param("page", "2").param("size", "2"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            List<Map<String, Object>> pageItems = JsonPath.read(body, "$.items");
            var expectedIds = all.subList(2, Math.min(4, all.size())).stream().map(item -> item.get("id")).toList();
            assertThat(pageItems).extracting(item -> item.get("id")).containsExactlyElementsOf(expectedIds);
            assertThat((int) JsonPath.read(body, "$.page")).isEqualTo(2);
            assertThat((int) JsonPath.read(body, "$.totalItems")).isEqualTo(all.size());
            assertThat((int) JsonPath.read(body, "$.totalPages")).isEqualTo((all.size() + 1) / 2);
        }

        @Test
        @DisplayName("状態が決められた値でなければ 400")
        void rejectsUnknownStatus() throws Exception {
            mockMvc.perform(get(BASE).param("status", "DELIVERED")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("AC-100、106 注文の詳細")
    class Detail {

        @Test
        @DisplayName("AC-100 明細・金額の内訳・メールアドレス・お届け先と、キャンセルの申請とキャンセルの内容を返す")
        void showsDetail() throws Exception {
            var orderId = orders.placeOrder(variantId, 2);
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "サイズ違い"}"""))
                    .andExpect(status().isNoContent());
            cancel(orderId);

            mockMvc.perform(get(BASE + "/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value(orderNumber(orderId)))
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.lines[0].variantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.lines[0].productName").value("T シャツ"))
                    .andExpect(jsonPath("$.lines[0].axisValues[0].name").value("色"))
                    .andExpect(jsonPath("$.lines[0].axisValues[0].value").value("白"))
                    .andExpect(jsonPath("$.lines[0].unitPriceExclTax").value(2700))
                    .andExpect(jsonPath("$.lines[0].unitPriceInclTax").value(2970))
                    .andExpect(jsonPath("$.lines[0].ratePercent").value(10))
                    .andExpect(jsonPath("$.lines[0].quantity").value(2))
                    .andExpect(jsonPath("$.lines[0].subtotal").value(5940))
                    .andExpect(jsonPath("$.amounts.total").value(6940))
                    .andExpect(jsonPath("$.amounts.taxes[0].taxableAmount").value(6940))
                    .andExpect(jsonPath("$.email").value("member@example.com"))
                    .andExpect(jsonPath("$.shippingAddress.name").value("山田 太郎"))
                    .andExpect(jsonPath("$.shippingAddress.prefectureCode").value("13"))
                    .andExpect(jsonPath("$.shippingAddress.prefectureName").value("東京都"))
                    .andExpect(jsonPath("$.shippingAddress.municipalityCode").value("13101"))
                    .andExpect(jsonPath("$.shippingAddress.municipalityName").value("千代田区"))
                    .andExpect(jsonPath("$.shippingAddress.address").value("千代田 1-1"))
                    .andExpect(jsonPath("$.cancellationRequest.reason").value("サイズ違い"))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("APPROVED"))
                    .andExpect(jsonPath("$.cancellationRequest.decidedAt").exists())
                    .andExpect(jsonPath("$.cancellation.kind").value("OPERATOR"))
                    .andExpect(jsonPath("$.cancellation.reason").value("在庫の不備"))
                    .andExpect(jsonPath("$.cancellation.cancelledAt").exists());
        }

        @Test
        @DisplayName("申請もキャンセルもない注文は、それらを返さない。存在しない注文は 404")
        void showsAcceptedOrder() throws Exception {
            var orderId = orders.placeOrder(variantId, 1);

            mockMvc.perform(get(BASE + "/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellationRequest").doesNotExist())
                    .andExpect(jsonPath("$.cancellation").doesNotExist());
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ============================== 補助 ==============================

    /**
     * 行の注文日（日本時間）
     */
    private static LocalDate orderedDate(Map<String, Object> item) {
        return Instant.parse((String) item.get("orderedAt")).atZone(JST).toLocalDate();
    }

    /**
     * 運営者の判断でキャンセルする
     */
    private void cancel(UUID orderId) throws Exception {
        mockMvc.perform(put(BASE + "/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "在庫の不備"}"""))
                .andExpect(status().isNoContent());
    }

    /**
     * 注文番号を C 側の API で取得する
     */
    private String orderNumber(UUID orderId) throws Exception {
        var body = mockMvc.perform(get("/api/store/orders/" + orderId)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderNumber");
    }
}
