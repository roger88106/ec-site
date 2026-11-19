package com.ecsite.module.order.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.api.model.AxisValue;
import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.module.order.api.model.TaxAmount;
import com.ecsite.module.order.event.OrderStatusChanged;
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
import com.ecsite.support.security.TestTokens;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の API の統合テスト
 *
 * <p>注文の参照について、状態ごとの項目と、個人の情報を持たないことを確認する。あわせて、注文のメールアドレスの操作を確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class OrderApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

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
     * 税抜単価（税率 10%、税込 1,100 円）
     */
    private static final int UNIT_PRICE_EXCL_TAX = 1000;

    /**
     * 支払待ちの注文の数量
     */
    private static final int QUANTITY = 2;

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

    @Autowired
    OrderApi orderApi;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * 商品（T シャツ・白）
     */
    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        shirt = catalog.product(catalog.taxCategory("標準", "10"), PRODUCT_NAME, UNIT_PRICE_EXCL_TAX, AXIS_NAME)
                .variant(AXIS_VALUE).publish();
        var variantId = shirt.variantIds.getFirst();
        inventory.stock(variantId, shirt.id, 10);
        orders = new OrderTestData(mockMvc, gateway, applicationContext);
        // 商品の準備で時計が進むので戻す
        clock.setInstant(NOW);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("支払待ちの注文")
    class PendingOrder {

        @Test
        @DisplayName("注文 ID・注文番号・会員・注文日時と、状態の支払待ちを返し、キャンセル日時は空")
        void returnsHeader() throws Exception {
            // 準備：支払待ちの注文
            var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), QUANTITY);

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：注文の見出しの項目
            assertHeader(snapshot, orderId);
        }

        @Test
        @DisplayName("お届け先は、郵便番号と都道府県だけを返す")
        void returnsPostalCodeAndPrefecture() throws Exception {
            // 準備：支払待ちの注文（お届け先は注文の補助の既定）
            var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), QUANTITY);

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：郵便番号はハイフンなし、都道府県はコード
            assertThat(snapshot.postalCode()).isEqualTo("1000001");
            assertThat(snapshot.prefectureCode()).isEqualTo("13");
        }

        @Test
        @DisplayName("token に生年月日と性別がない会員の注文は、年齢と性別が空")
        void returnsEmptyBuyerProfile() throws Exception {
            // 準備：既定の会員（token に生年月日と性別がない）の支払待ちの注文
            var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), QUANTITY);

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：年齢も性別も空
            assertThat(snapshot.buyerAge()).isNull();
            assertThat(snapshot.buyerGender()).isNull();
        }

        @Test
        @DisplayName("総額・送料・税率ごとの消費税額・送料の消費税を返す")
        void returnsAmounts() throws Exception {
            // 準備：税込 1,100 円の商品 2 個の支払待ちの注文
            var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), QUANTITY);

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：金額の項目
            assertAmounts(snapshot);
        }

        @Test
        @DisplayName("明細の、注文時点の商品・税抜単価・税込小計を返す")
        void returnsLine() throws Exception {
            // 準備：税込 1,100 円の商品 2 個の支払待ちの注文
            var orderId = orders.placePendingOrder(shirt.variantIds.getFirst(), QUANTITY);

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：明細が 1 行で、その項目
            assertThat(snapshot.lines()).singleElement()
                    .satisfies(line -> assertLine(line, shirt.variantIds.getFirst()));
        }
    }

    @Nested
    @DisplayName("支払待ちでない注文")
    class OtherStatus {

        @Test
        @DisplayName("出荷済みの注文は、状態が出荷済み")
        void returnsShippedOrder() throws Exception {
            // 準備：受付の注文を出荷する
            var orderId = orders.placeOrder(shirt.variantIds.getFirst(), 1);
            new ShippingTestData(mockMvc, mockCarrier, applicationContext).ship(orderId, Carrier.YAMATO, "123456789012");

            // 操作と確認：状態が出荷済み
            assertThat(orderApi.find(orderId).orElseThrow().status()).isEqualTo(OrderStatusChanged.Status.SHIPPED);
        }

        @Test
        @DisplayName("キャンセルの注文は、キャンセル日時、種類、在庫に戻したかを返す")
        void returnsCancelledOrder() throws Exception {
            // 準備：受付の注文を、1 時間後に運営者がキャンセルする
            var orderId = orders.placeOrder(shirt.variantIds.getFirst(), 1);
            var cancelledAt = NOW.plus(Duration.ofHours(1));
            clock.setInstant(cancelledAt);
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "在庫の誤り"}"""))
                    .andExpect(status().isNoContent());

            // 操作：注文を読む
            var snapshot = orderApi.find(orderId).orElseThrow();

            // 確認：キャンセルの項目
            assertThat(snapshot.status()).isEqualTo(OrderStatusChanged.Status.CANCELLED);
            assertThat(snapshot.cancellation()).isNotNull();
            assertThat(snapshot.cancellation().cancelledAt()).isEqualTo(cancelledAt);
            assertThat(snapshot.cancellation().kind()).isEqualTo(OrderStatusChanged.CancellationKind.OPERATOR);
            assertThat(snapshot.cancellation().restocked()).isTrue();
        }

        @Test
        @DisplayName("ない注文は空")
        void returnsEmptyForUnknownOrder() {
            // 操作と確認：ない注文 ID には空
            assertThat(orderApi.find(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("注文のメールアドレス")
    class FindEmail {

        @Test
        @DisplayName("DC-572-3 注文時に写した会員のメールアドレスを返す")
        void returnsEmailOfOrder() throws Exception {
            // 準備：既定の会員の受付の注文
            var orderId = orders.placeOrder(shirt.variantIds.getFirst(), 1);

            // 操作と確認：会員のメールアドレス
            assertThat(orderApi.findEmail(orderId)).contains(TestTokens.MEMBER_EMAIL);
        }

        @Test
        @DisplayName("DC-572-3 ない注文は空")
        void returnsEmptyForUnknownOrder() {
            // 操作と確認：ない注文 ID には空
            assertThat(orderApi.findEmail(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("項目の構成")
    class Structure {

        @Test
        @DisplayName("NFR-16 注文に、氏名・住所・電話番号・メールアドレスの項目を持たない")
        void excludesPersonalInformation() {
            // 準備：注文の全ての項目を、入れ子の record の中まで集める
            var components = new ArrayList<String>();
            collectRecordComponents(OrderSnapshot.class, components);

            // 操作：名前に個人の情報を表す語を含む項目を選ぶ（商品名と軸の名前は、個人の情報ではない）
            var personal = components.stream()
                    .filter(path -> !path.equals("Line.productName") && !path.equals("AxisValue.name"))
                    .filter(path -> {
                        var name = path.substring(path.indexOf('.') + 1).toLowerCase(Locale.ROOT);
                        return name.contains("name") || name.contains("address")
                                || name.contains("phone") || name.contains("email");
                    })
                    .toList();

            // 確認（前提）：入れ子の record の項目まで集めている
            assertThat(components).contains("Line.productName", "AxisValue.name");

            // 確認：個人の情報の項目がない
            assertThat(personal).isEmpty();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 支払待ちの注文の、見出しの項目を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>注文 ID</li>
     *   <li>注文番号が、注文日（日本時間の 2026-10-08）と 6 桁の数字</li>
     *   <li>会員 ID がある</li>
     *   <li>注文日時が今の時刻</li>
     *   <li>状態が支払待ち</li>
     *   <li>キャンセル日時が空</li>
     * </ul>
     *
     * @param snapshot 注文の今の値
     * @param orderId  注文 ID（注文のときに作るので、定数にできない）
     */
    private static void assertHeader(OrderSnapshot snapshot, UUID orderId) {
        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.orderNumber()).matches("20261008-\\d{6}");
        assertThat(snapshot.memberId()).isNotNull();
        assertThat(snapshot.orderedAt()).isEqualTo(NOW);
        assertThat(snapshot.status()).isEqualTo(OrderStatusChanged.Status.PENDING_PAYMENT);
        assertThat(snapshot.cancellation()).isNull();
    }

    /**
     * 税込 1,100 円の商品 2 個の注文の、金額の項目を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>総額が、1,100 円 × 2 ＋ 送料 1,000 円の 3,200 円</li>
     *   <li>送料が 1,000 円（注文の設定の既定）</li>
     *   <li>税率ごとの消費税額が 1 つだけで、3,200 × 10 / 110 を切り捨てた 290 円</li>
     *   <li>送料の消費税が、1,000 × 10 / 110 の切り捨ての 90 円</li>
     * </ul>
     *
     * @param snapshot 注文の今の値
     */
    private static void assertAmounts(OrderSnapshot snapshot) {
        var amounts = snapshot.amounts();
        assertThat(amounts.total()).isEqualTo(Money.of(3200));
        assertThat(amounts.shippingFee()).isEqualTo(Money.of(1000));
        assertThat(amounts.taxes()).extracting(TaxAmount::taxAmount).containsExactly(Money.of(290));
        assertThat(snapshot.shippingTax()).isEqualTo(Money.of(90));
    }

    /**
     * 明細が、税込 1,100 円の商品 2 個の注文であることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>行番号が 1</li>
     *   <li>バリアント ID</li>
     *   <li>商品名</li>
     *   <li>軸の名前と値</li>
     *   <li>数量</li>
     *   <li>税率が 10%</li>
     *   <li>税抜単価が 1,000 円、税込の小計が 2,200 円</li>
     * </ul>
     *
     * @param line      明細
     * @param variantId バリアント ID（商品の登録のときに作るので、定数にできない）
     */
    private static void assertLine(OrderSnapshot.Line line, UUID variantId) {
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.variantId()).isEqualTo(variantId);
        assertThat(line.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(line.axisValues()).containsExactly(new AxisValue(AXIS_NAME, AXIS_VALUE));
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.ratePercent()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(line.unitPriceExclTax()).isEqualTo(Money.of(UNIT_PRICE_EXCL_TAX));
        assertThat(line.subtotalInclTax()).isEqualTo(Money.of(2200));
    }

    // ============================== 準備の補助 ==============================

    /**
     * record の項目を「型名.項目名」で集める。
     *
     * <p>項目の型が record のとき、List の要素の型が record のときは、その中まで集める。
     *
     * @param type  record の型
     * @param paths 集めた項目（ここに加える）
     */
    private static void collectRecordComponents(Class<?> type, List<String> paths) {
        for (var component : type.getRecordComponents()) {
            paths.add(type.getSimpleName() + "." + component.getName());
            collectRecordComponents(component.getGenericType(), paths);
        }
    }

    /**
     * 型が record のとき、または record を要素に持つ List などのとき、その中の項目を集める。
     *
     * @param type  項目の型
     * @param paths 集めた項目（ここに加える）
     */
    private static void collectRecordComponents(Type type, List<String> paths) {
        if (type instanceof Class<?> clazz && clazz.isRecord()) {
            collectRecordComponents(clazz, paths);
        } else if (type instanceof ParameterizedType parameterized) {
            for (var argument : parameterized.getActualTypeArguments()) {
                collectRecordComponents(argument, paths);
            }
        }
    }
}
