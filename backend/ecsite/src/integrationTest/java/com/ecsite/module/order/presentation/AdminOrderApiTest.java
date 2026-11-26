package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * B 側の注文の一覧・詳細の受け入れテスト
 *
 * <p>一覧の並びと項目、絞り込み、ページ、詳細の項目を確認する。出荷と与信期限の表示と絞り込みは
 * {@code AdminOrderShipmentViewApiTest} が確認する。
 *
 * <p>注文は C 側の API で作る。一覧は他のテストの注文も含むので、自分の注文を ID で選んで確認する（{@link AdminOrderLists}）。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class AdminOrderApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/orders";

    /**
     * 日本時間
     */
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /**
     * 日付をまたぐ境界の確認に使う日時
     *
     * <p>日本時間の 2026-12-08 23:59。
     */
    private static final Instant DEC_8_LATE = Instant.parse("2026-12-08T14:59:00Z");

    /**
     * 日付をまたいだ後の日時
     *
     * <p>日本時間の 2026-12-09 00:00。
     */
    private static final Instant DEC_9 = Instant.parse("2026-12-08T15:00:00Z");

    /**
     * ページの確認で注文する日（他のテストが注文を作らない日。一覧をこの日で絞り、他のテストの注文を除く）
     */
    private static final LocalDate PAGED_DAY = LocalDate.of(2032, 3, 10);

    /**
     * ページの確認で、最初に注文する日時
     *
     * <p>日本時間の 2032-03-10 10:00。
     */
    private static final Instant PAGED_DAY_START = Instant.parse("2032-03-10T01:00:00Z");

    /**
     * ページの確認で注文する数
     */
    private static final int PAGED_ORDERS = 5;

    /**
     * ページの確認の、1 ページの件数
     */
    private static final int PAGE_SIZE = 2;

    /**
     * ページの確認で取得するページの番号
     */
    private static final int PAGE = 2;

    /**
     * ページの確認の、全体のページ数（5 件を 2 件ずつ）
     */
    private static final int PAGED_TOTAL_PAGES = 3;

    /**
     * 商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    /**
     * 軸の名前
     */
    private static final String AXIS_NAME = "色";

    /**
     * 軸の値
     */
    private static final String AXIS_VALUE = "白";

    /**
     * 税抜単価
     */
    private static final int UNIT_PRICE_EXCL_TAX = 2700;

    /**
     * 税率（%）
     */
    private static final int RATE_PERCENT = 10;

    /**
     * 税込単価（税抜単価に税率 10% を加えた額）
     */
    private static final int UNIT_PRICE_INCL_TAX = 2970;

    /**
     * 送料（注文の設定 {@code ecsite.order.shipping-fee} の既定）
     */
    private static final int SHIPPING_FEE = Math.toIntExact(OrderTestData.SHIPPING_FEE);

    /**
     * お届け先の氏名（注文の補助の既定）
     */
    private static final String RECIPIENT_NAME = OrderTestData.RECIPIENT_NAME;

    /**
     * 注文のメールアドレス（既定の会員の token のメールアドレス）
     */
    private static final String MEMBER_EMAIL = TestTokens.MEMBER_EMAIL;

    /**
     * 消費者のキャンセルの申請の理由
     */
    private static final String REQUEST_REASON = "サイズ違い";

    /**
     * 運営者の判断によるキャンセルの理由
     */
    private static final String CANCEL_REASON = "在庫の不備";

    // ============================== Bean ==============================

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

    /**
     * 商品のバリアント ID
     */
    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(DEC_8_LATE.minusSeconds(3600));
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準", String.valueOf(RATE_PERCENT)), PRODUCT_NAME,
                UNIT_PRICE_EXCL_TAX, AXIS_NAME).variant(AXIS_VALUE).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 50);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        lists = new AdminOrderLists(mockMvc);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文一覧")
    class ListOrders {

        @Test
        @DisplayName("AC-97 注文日時の新しい順に並ぶ")
        void listsNewestFirst() throws Exception {
            // 準備：12 月 8 日と 9 日に 1 件ずつ注文する
            clock.setInstant(DEC_8_LATE);
            var older = orders.placeOrder(variantId, 1, "佐藤 花子");
            clock.setInstant(DEC_9);
            var newer = orders.placeOrder(variantId, 2, RECIPIENT_NAME);

            // 操作：一覧を取得する
            var items = lists.listAll();

            // 確認：新しい注文が先
            assertThat(idsOf(items, older, newer)).containsExactly(newer.toString(), older.toString());
        }

        @Test
        @DisplayName("AC-97 各行に、注文番号・注文日時・氏名・総額・状態を返す")
        void showsColumns() throws Exception {
            // 準備：12 月 9 日に 2 個を注文する
            clock.setInstant(DEC_9);
            var orderId = orders.placeOrder(variantId, 2, RECIPIENT_NAME);

            // 操作：一覧を取得する
            var item = itemOf(lists.listAll(), orderId);

            // 確認：行の項目が注文の内容
            assertSummary(item, orderNumber(orderId));
        }

        @Test
        @DisplayName("AC-106 キャンセル済みの注文も一覧に含まれる")
        void includesCancelled() throws Exception {
            // 準備：注文して、運営者がキャンセルする
            var orderId = orders.placeOrder(variantId, 1);
            cancel(orderId);

            // 操作：一覧を取得する
            var items = lists.listAll();

            // 確認：キャンセルの状態で並ぶ
            assertThat(itemOf(items, orderId).get("status")).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("AC-99 ページ単位で取得できる")
        void pages() throws Exception {
            // 準備：他のテストが使わない日に、1 分ずつずらして 5 件を注文する（古い順に並べて持つ）
            var placed = new ArrayList<UUID>();
            for (int i = 0; i < PAGED_ORDERS; i++) {
                clock.setInstant(PAGED_DAY_START.plus(Duration.ofMinutes(i)));
                placed.add(orders.placeOrder(variantId, 1));
            }

            // 操作：その日の注文だけに絞り、1 ページ 2 件の 2 ページ目を取得する
            var body = mockMvc.perform(get(BASE)
                            .param("orderedFrom", PAGED_DAY.toString()).param("orderedTo", PAGED_DAY.toString())
                            .param("page", String.valueOf(PAGE)).param("size", String.valueOf(PAGE_SIZE)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            // 確認：新しい順で 3、4 件目（古い方から 3、2 件目）が並び、ページの情報がその日の 5 件と合う
            List<Map<String, Object>> pageItems = JsonPath.read(body, "$.items");
            assertThat(pageItems).extracting(item -> item.get("id"))
                    .containsExactly(placed.get(2).toString(), placed.get(1).toString());
            assertThat((int) JsonPath.read(body, "$.page")).isEqualTo(PAGE);
            assertThat((int) JsonPath.read(body, "$.totalItems")).isEqualTo(PAGED_ORDERS);
            assertThat((int) JsonPath.read(body, "$.totalPages")).isEqualTo(PAGED_TOTAL_PAGES);
        }

        @Test
        @DisplayName("状態が決められた値でなければ 400")
        void rejectsUnknownStatus() throws Exception {
            // 操作と確認：一覧にない状態は 400
            mockMvc.perform(get(BASE).param("status", "DELIVERED")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("注文一覧の絞り込み")
    class FilterOrders {

        /**
         * 日本時間の 12 月 8 日 23:59 の注文（受付）
         */
        private UUID dec8;

        /**
         * 日本時間の 12 月 9 日 00:00 の注文（キャンセル）
         */
        private UUID dec9;

        @BeforeEach
        void setUpOrders() throws Exception {
            clock.setInstant(DEC_8_LATE);
            dec8 = orders.placeOrder(variantId, 1);
            clock.setInstant(DEC_9);
            dec9 = orders.placeOrder(variantId, 1);
            cancel(dec9);
        }

        @Test
        @DisplayName("AC-98 注文番号の先頭の部分で絞り込める")
        void filtersByOrderNumberPrefix() throws Exception {
            // 操作：注文番号の日付の部分で絞り込む
            var items = lists.listAll("orderNumber", "20261209");

            // 確認：12 月 9 日の注文だけで、全ての行の注文番号がその日付で始まる
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(items).allSatisfy(item -> assertThat((String) item.get("orderNumber")).startsWith("20261209"));
        }

        @Test
        @DisplayName("AC-98 注文番号の全体で絞り込むと、その注文だけになる")
        void filtersByWholeOrderNumber() throws Exception {
            // 操作：12 月 8 日の注文の番号で絞り込む
            var number = orderNumber(dec8);
            var items = lists.listAll("orderNumber", number);

            // 確認：その注文だけで、全ての行がその番号
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec8.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("orderNumber")).isEqualTo(number));
        }

        @Test
        @DisplayName("AC-98 状態で絞り込める")
        void filtersByStatus() throws Exception {
            // 操作：キャンセルで絞り込む
            var items = lists.listAll("status", "CANCELLED");

            // 確認：キャンセルした注文だけで、全ての行がキャンセル
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(items).allSatisfy(item -> assertThat(item.get("status")).isEqualTo("CANCELLED"));
        }

        @Test
        @DisplayName("AC-98 注文日の始まりで絞り込むと、その日（日本時間）からの注文になる")
        void filtersByOrderedFrom() throws Exception {
            // 操作：12 月 9 日からで絞り込む
            var items = lists.listAll("orderedFrom", "2026-12-09");

            // 確認：日本時間の 0 時ちょうどの注文を含み、前日の 23:59 の注文を含まない
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec9.toString());
            assertThat(items).allSatisfy(item -> assertThat(orderedDate(item)).isAfterOrEqualTo(LocalDate.of(2026, 12, 9)));
        }

        @Test
        @DisplayName("AC-98 注文日の終わりで絞り込むと、その日（日本時間）までの注文になる")
        void filtersByOrderedTo() throws Exception {
            // 操作：12 月 8 日までで絞り込む
            var items = lists.listAll("orderedTo", "2026-12-08");

            // 確認：日本時間の 23:59 の注文を含み、翌日の 0 時の注文を含まない
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec8.toString());
            assertThat(items).allSatisfy(item -> assertThat(orderedDate(item)).isBeforeOrEqualTo(LocalDate.of(2026, 12, 8)));
        }

        @Test
        @DisplayName("AC-98 注文日の範囲は、始まりと終わりの日を含む")
        void filtersByOrderedRange() throws Exception {
            // 操作：12 月 8 日から 9 日までで絞り込む
            var items = lists.listAll("orderedFrom", "2026-12-08", "orderedTo", "2026-12-09");

            // 確認：両日の注文が新しい順に並び、全ての行が範囲の中
            assertThat(idsOf(items, dec8, dec9)).containsExactly(dec9.toString(), dec8.toString());
            assertThat(items).allSatisfy(item -> assertThat(orderedDate(item))
                    .isBetween(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9)));
        }
    }

    @Nested
    @DisplayName("注文の詳細")
    class Detail {

        /**
         * 消費者がキャンセルを申請し、運営者がキャンセルした注文
         */
        private UUID orderId;

        @BeforeEach
        void setUpCancelledOrder() throws Exception {
            // 12 月 8 日に注文し、12 月 9 日に申請があり、運営者がキャンセルする
            clock.setInstant(DEC_8_LATE);
            orderId = orders.placeOrder(variantId, 2);
            clock.setInstant(DEC_9);
            orders.requestCancellation(orderId, REQUEST_REASON);
            cancel(orderId);
        }

        @Test
        @DisplayName("AC-100 注文番号と注文日時を返す")
        void showsOrderNumberAndOrderedAt() throws Exception {
            // 操作と確認：C 側の注文番号と、注文した日時
            detail(orderId)
                    .andExpect(jsonPath("$.orderNumber").value(orderNumber(orderId)))
                    .andExpect(jsonPath("$.orderedAt").value(DEC_8_LATE.toString()));
        }

        @Test
        @DisplayName("AC-100、106 キャンセル済みの注文も詳細を返し、状態はキャンセル")
        void showsCancelledOrder() throws Exception {
            // 操作と確認：200 で、状態がキャンセル
            detail(orderId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("AC-100 明細を、注文時点の写しで返す")
        void showsLines() throws Exception {
            // 操作と確認：明細の項目が、注文した商品の値
            assertLine(detail(orderId));
        }

        @Test
        @DisplayName("AC-100 金額の内訳を返す")
        void showsAmounts() throws Exception {
            // 操作と確認：小計・送料・総額と、税率ごとの対象額と消費税額
            assertAmounts(detail(orderId));
        }

        @Test
        @DisplayName("AC-100 メールアドレスとお届け先を返す")
        void showsEmailAndAddress() throws Exception {
            // 操作と確認：注文のメールアドレスと、お届け先の全ての項目
            assertEmailAndAddress(detail(orderId));
        }

        @Test
        @DisplayName("AC-100 キャンセルの申請の日時と理由と結果を返す")
        void showsCancellationRequest() throws Exception {
            // 操作と確認：申請した日時と理由、運営者のキャンセルで承認になった結果
            detail(orderId)
                    .andExpect(jsonPath("$.cancellationRequest.requestedAt").value(DEC_9.toString()))
                    .andExpect(jsonPath("$.cancellationRequest.reason").value(REQUEST_REASON))
                    .andExpect(jsonPath("$.cancellationRequest.result").value("APPROVED"))
                    .andExpect(jsonPath("$.cancellationRequest.decidedAt").exists());
        }

        @Test
        @DisplayName("AC-100 運営者の判断によるキャンセルの理由を返す")
        void showsCancellation() throws Exception {
            // 操作と確認：運営者のキャンセルで、入力した理由
            detail(orderId)
                    .andExpect(jsonPath("$.cancellation.kind").value("OPERATOR"))
                    .andExpect(jsonPath("$.cancellation.reason").value(CANCEL_REASON))
                    .andExpect(jsonPath("$.cancellation.cancelledAt").exists());
        }
    }

    @Nested
    @DisplayName("申請もキャンセルもない注文と、ない注文の詳細")
    class OtherDetail {

        @Test
        @DisplayName("申請もキャンセルもない注文は、それらを返さない")
        void showsAcceptedOrder() throws Exception {
            // 準備：注文する
            var orderId = orders.placeOrder(variantId, 1);

            // 操作と確認：受付で、申請とキャンセルの項目がない
            detail(orderId)
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.cancellationRequest").doesNotExist())
                    .andExpect(jsonPath("$.cancellation").doesNotExist());
        }

        @Test
        @DisplayName("存在しない注文は 404")
        void notFound() throws Exception {
            // 操作と確認：ない注文 ID には 404
            detail(UUID.randomUUID()).andExpect(status().isNotFound());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 一覧の行が、12 月 9 日の 2 個の注文の内容であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>氏名がお届け先の氏名</li>
     *   <li>総額が、税込単価 × 2 に送料を加えた額</li>
     *   <li>状態が受付</li>
     *   <li>注文番号が C 側の注文番号</li>
     *   <li>注文日時が 12 月 9 日 00:00（日本時間）</li>
     * </ul>
     *
     * @param item        一覧の行
     * @param orderNumber 注文番号（注文のときに採番されるので、定数にできない）
     */
    private static void assertSummary(Map<String, Object> item, String orderNumber) {
        assertThat(item.get("customerName")).isEqualTo(RECIPIENT_NAME);
        assertThat(item.get("total")).isEqualTo(UNIT_PRICE_INCL_TAX * 2 + SHIPPING_FEE);
        assertThat(item.get("status")).isEqualTo("ACCEPTED");
        assertThat(item.get("orderNumber")).isEqualTo(orderNumber);
        assertThat(item.get("orderedAt")).isEqualTo(DEC_9.toString());
    }

    /**
     * 詳細の明細が、2 個の注文の写しであることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>バリアント ID</li>
     *   <li>商品名</li>
     *   <li>軸の名前と値</li>
     *   <li>税抜単価、税込単価、税率</li>
     *   <li>数量、小計</li>
     * </ul>
     *
     * @param detail 詳細の応答
     */
    private void assertLine(ResultActions detail) throws Exception {
        detail.andExpect(jsonPath("$.lines[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.lines[0].productName").value(PRODUCT_NAME))
                .andExpect(jsonPath("$.lines[0].axisValues[0].name").value(AXIS_NAME))
                .andExpect(jsonPath("$.lines[0].axisValues[0].value").value(AXIS_VALUE))
                .andExpect(jsonPath("$.lines[0].unitPriceExclTax").value(UNIT_PRICE_EXCL_TAX))
                .andExpect(jsonPath("$.lines[0].unitPriceInclTax").value(UNIT_PRICE_INCL_TAX))
                .andExpect(jsonPath("$.lines[0].ratePercent").value(RATE_PERCENT))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].subtotal").value(UNIT_PRICE_INCL_TAX * 2));
    }

    /**
     * 詳細の金額の内訳が、2 個の注文の金額であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>商品の小計が、税込単価 × 2</li>
     *   <li>送料</li>
     *   <li>総額が、小計に送料を加えた額</li>
     *   <li>税率 10% の対象額が総額（送料も 10%）</li>
     *   <li>税率 10% の消費税額が、総額 × 10 / 110 の切り捨て</li>
     * </ul>
     *
     * @param detail 詳細の応答
     */
    private static void assertAmounts(ResultActions detail) throws Exception {
        var total = UNIT_PRICE_INCL_TAX * 2 + SHIPPING_FEE;
        detail.andExpect(jsonPath("$.amounts.itemsTotal").value(UNIT_PRICE_INCL_TAX * 2))
                .andExpect(jsonPath("$.amounts.shippingFee").value(SHIPPING_FEE))
                .andExpect(jsonPath("$.amounts.total").value(total))
                .andExpect(jsonPath("$.amounts.taxes[0].taxableAmount").value(total))
                .andExpect(jsonPath("$.amounts.taxes[0].taxAmount").value(total * 10 / 110));
    }

    /**
     * 詳細のメールアドレスとお届け先が、注文の補助の既定の値であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>メールアドレスが既定の会員のもの</li>
     *   <li>お届け先の氏名、都道府県のコードと名前、市区町村のコードと名前、住所</li>
     * </ul>
     *
     * @param detail 詳細の応答
     */
    private static void assertEmailAndAddress(ResultActions detail) throws Exception {
        detail.andExpect(jsonPath("$.email").value(MEMBER_EMAIL))
                .andExpect(jsonPath("$.shippingAddress.name").value(RECIPIENT_NAME))
                .andExpect(jsonPath("$.shippingAddress.prefectureCode").value(OrderTestData.PREFECTURE_CODE))
                .andExpect(jsonPath("$.shippingAddress.prefectureName").value("東京都"))
                .andExpect(jsonPath("$.shippingAddress.municipalityCode").value(OrderTestData.MUNICIPALITY_CODE))
                .andExpect(jsonPath("$.shippingAddress.municipalityName").value("千代田区"))
                .andExpect(jsonPath("$.shippingAddress.address").value(OrderTestData.ADDRESS));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 行の注文日（日本時間）を返す。
     *
     * @param item 一覧の行
     * @return 注文日
     */
    private static LocalDate orderedDate(Map<String, Object> item) {
        return Instant.parse((String) item.get("orderedAt")).atZone(JST).toLocalDate();
    }

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
     * 運営者の判断でキャンセルする。
     *
     * @param orderId 注文 ID
     */
    private void cancel(UUID orderId) throws Exception {
        mockMvc.perform(put(BASE + "/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "%s"}""".formatted(CANCEL_REASON)))
                .andExpect(status().isNoContent());
    }

    /**
     * 注文番号を C 側の API で取得する。
     *
     * @param orderId 注文 ID
     * @return 注文番号
     */
    private String orderNumber(UUID orderId) throws Exception {
        var body = mockMvc.perform(get("/api/store/orders/" + orderId)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderNumber");
    }
}
