package com.ecsite.module.order.presentation;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.job.PaymentJobs;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.data.ShippingTestData;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 出荷の登録の受け入れテスト
 *
 * <p>管理 API で出荷を登録したときの、売上確定と注文の状態、失敗と重ねた登録、入力の検証を確認する。
 * 出荷の可否の判定そのものは {@code OrderShipmentTest}、店舗の注文の出荷の表示は {@code StoreOrderShipmentApiTest} が確認する。
 *
 * <p>売上確定は mock の決済サービスで行う。テストごとに別の ID でデータを作り、コミットする（出荷の登録のエンドポイントは、売上の確定をトランザクションの外で行うため、ロールバックできない）。
 */
@IntegrationTest
@RecordApplicationEvents
class ShipmentApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 商品の税抜単価（税率 10%、税込 1,100 円）
     */
    private static final int UNIT_PRICE_EXCL_TAX = 1000;

    /**
     * 注文の数量
     */
    private static final int QUANTITY = 2;

    /**
     * 注文の総額（売上確定の額）
     *
     * <p>税込単価 1,100 円 × 数量 2 の 2,200 円に、送料 1,000 円（注文の設定 {@code ecsite.order.shipping-fee} の既定。
     * 統合テストの設定は上書きしない）を加えた額。
     */
    private static final long ORDER_TOTAL = 3200;

    /**
     * 形の正しい送り状番号（入力の検証で拒否される、または注文がない要求に使う。出荷は保存されない）
     */
    private static final String UNSAVED_TRACKING_NUMBER = "123456789012";

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
    PaymentProperties properties;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    DSLContext dsl;

    /**
     * テストの中で発行されたイベント（出荷のイベントの配送業者を確認する）
     */
    @Autowired
    ApplicationEvents events;

    /**
     * 注文する会員（コミットするので、テストごとの会員にする）
     */
    private TestTokens.Builder owner;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * 商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 出荷を登録する注文の ID（「受付」）
     */
    private UUID orderId;

    /**
     * 送り状番号（ハイフンなし。コミットした出荷が他のテストの通知と重ならないように、テストごとに作る）
     */
    private String tracking;

    /**
     * 送り状番号（ハイフン付き。登録するとハイフンを除いた {@link #tracking} になる）
     */
    private String trackingWithHyphens;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var product = catalog.product(catalog.taxCategory("標準 " + UUID.randomUUID(), "10"), "商品", UNIT_PRICE_EXCL_TAX).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 5);
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer());
        orderId = orders.placeOrder(variantId, QUANTITY);
        tracking = ShippingTestData.trackingNumber();
        trackingWithHyphens = tracking.substring(0, 4) + "-" + tracking.substring(4, 8) + "-" + tracking.substring(8);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷の登録")
    class Ship {

        @Test
        @DisplayName("AC-167 「受付」の注文に出荷を登録すると 201 で、配送業者・送り状番号・状態・出荷日時を返す")
        void returnsShipment() throws Exception {
            // 準備：注文の 1 時間後に登録する
            clock.setInstant(NOW.plusSeconds(3600));

            // 操作と確認：送り状番号はハイフンを除いて返り、出荷日時は登録の時刻
            ship("YAMATO", trackingWithHyphens).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.carrier").value("YAMATO"))
                    .andExpect(jsonPath("$.trackingNumber").value(tracking))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.shippedAt").value("2026-10-07T16:00:00Z"));
        }

        @Test
        @DisplayName("AC-168 出荷を登録すると、売上を確定し、注文は「出荷済み」になる")
        void capturesAndMarksShipped() throws Exception {
            // 操作：出荷を登録する
            ship("YAMATO", trackingWithHyphens).andExpect(status().isCreated());

            // 確認：注文は出荷済みで、支払は売上確定済み、出荷が付いている
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value(tracking));

            // 確認：決済サービスで売上を確定したのは、この注文だけ
            assertThat(gateway.captured()).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-168 出荷を登録すると、決済サービスに、注文の総額（送料を含む）を売上確定の額として渡す")
        void capturesOrderTotal() throws Exception {
            // 操作：出荷を登録する
            ship("YAMATO", trackingWithHyphens).andExpect(status().isCreated());

            // 確認（前提）：注文の総額は、商品の税込の小計に送料を加えた額
            var total = dsl.select(SALES_ORDER.TOTAL_AMOUNT).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                    .fetchOne(SALES_ORDER.TOTAL_AMOUNT);
            assertThat(total).isEqualTo(ORDER_TOTAL);

            // 確認：売上確定の額は、注文の総額
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId))
                    .containsExactly(Money.of(ORDER_TOTAL));
        }

        @Test
        @DisplayName("AC-170、171 出荷済みの注文の詳細は、出荷の登録もキャンセルもできないことを示す")
        void shippedOrderHasNoActions() throws Exception {
            // 準備：出荷を登録する
            ship("YAMATO", trackingWithHyphens).andExpect(status().isCreated());

            // 操作と確認：出荷の登録とキャンセルの操作が、どちらもできない
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.ship").value(false))
                    .andExpect(jsonPath("$.actions.cancel").value(false));
        }

        @Test
        @DisplayName("AC-167 「受付」の注文の詳細は、出荷を登録できることを示す")
        void acceptedOrderIsShippable() throws Exception {
            // 操作と確認：出荷の登録ができる
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.ship").value(true));
        }

        @Test
        @DisplayName("AC-268 「受付」の注文の詳細は、キャンセルできることを示し、在庫に戻すかは聞かない")
        void acceptedOrderCancelDoesNotAskRestock() throws Exception {
            // 操作と確認：キャンセルはできるが、在庫に戻すかを聞かない
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.cancel").value(true))
                    .andExpect(jsonPath("$.actions.cancelAsksRestock").value(false));
        }
    }

    @Nested
    @DisplayName("配送業者")
    class Carriers {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"YAMATO", "SAGAWA", "JAPAN_POST"})
        @DisplayName("AC-167 登録した配送業者を、応答と、管理と店舗の注文詳細に同じ業者で返す")
        void showsSameCarrier(String carrier) throws Exception {
            // 操作：配送業者を指定して出荷を登録する
            ship(carrier, tracking).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.carrier").value(carrier));

            // 確認：管理と店舗の注文詳細の出荷が、登録した業者
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.shipment.carrier").value(carrier));
            mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer()))
                    .andExpect(jsonPath("$.shipment.carrier").value(carrier));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"YAMATO", "SAGAWA", "JAPAN_POST"})
        @DisplayName("AC-183 出荷通知に使う注文の出荷のイベントに、登録した配送業者を載せる")
        void publishesSameCarrier(String carrier) throws Exception {
            // 操作：配送業者を指定して出荷を登録する
            ship(carrier, tracking).andExpect(status().isCreated());

            // 確認：この注文の出荷のイベントが 1 件で、業者が登録した業者
            assertThat(events.stream(OrderShipped.class).filter(event -> event.orderId().equals(orderId)))
                    .singleElement()
                    .extracting(event -> event.carrier().name())
                    .isEqualTo(carrier);
        }
    }

    @Nested
    @DisplayName("売上確定の失敗")
    class CaptureFailure {

        @Test
        @DisplayName("AC-169、188 与信期限切れなら 409 で、失敗の理由に与信期限切れを示す")
        void rejectsExpiredAuthorization() throws Exception {
            // 準備：決済サービスで与信を切らす
            gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(orderId)));

            // 操作と確認：409 の AUTHORIZATION_EXPIRED
            ship("YAMATO", tracking).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_EXPIRED"));
        }

        @Test
        @DisplayName("AC-169、188 与信期限切れで失敗すると、出荷は作られず「受付」のままで、支払は与信期限切れになる")
        void keepsAcceptedWhenAuthorizationExpired() throws Exception {
            // 準備：決済サービスで与信を切らす
            gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(orderId)));

            // 操作：出荷を登録して失敗する
            ship("YAMATO", tracking).andExpect(status().isConflict());

            // 確認：受付のまま、出荷はなく、支払は与信期限切れ
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZATION_EXPIRED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist());
        }

        @Test
        @DisplayName("AC-188 与信期限切れで出荷できなかった注文は、運営者がキャンセルできることを示す")
        void expiredOrderIsCancellable() throws Exception {
            // 準備：与信を切らし、出荷の登録に失敗する
            gateway.expireAuthorization(gateway.authorizationRefOf(orders.paidPageId(orderId)));
            ship("YAMATO", tracking).andExpect(status().isConflict());

            // 操作と確認：キャンセルの操作ができる
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.cancel").value(true));
        }

        @Test
        @DisplayName("AC-169 決済サービスに接続できなければ 502 で、失敗の理由に接続の失敗を示す")
        void rejectsWhenGatewayUnavailable() throws Exception {
            // 準備：次の売上確定を接続の失敗にする
            gateway.failNextCaptures(1);

            // 操作と確認：502 の PAYMENT_GATEWAY_UNAVAILABLE
            ship("YAMATO", tracking).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_GATEWAY_UNAVAILABLE"));
        }

        @Test
        @DisplayName("AC-169 決済サービスに接続できずに失敗すると、出荷は作られず「受付」のままで、支払は与信済みのまま")
        void keepsAcceptedWhenGatewayUnavailable() throws Exception {
            // 準備：次の売上確定を接続の失敗にする
            gateway.failNextCaptures(1);

            // 操作：出荷を登録して失敗する
            ship("YAMATO", tracking).andExpect(status().isBadGateway());

            // 確認：受付のまま、支払は与信済み、出荷はない
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist());
        }

        @Test
        @DisplayName("AC-169 決済サービスに接続できずに失敗した後、登録し直すと出荷できる")
        void shipsOnRetryAfterGatewayUnavailable() throws Exception {
            // 準備：1 回目の売上確定を接続の失敗にし、出荷の登録に失敗する
            gateway.failNextCaptures(1);
            ship("YAMATO", tracking).andExpect(status().isBadGateway());

            // 操作と確認：登録し直すと 201
            ship("YAMATO", tracking).andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("与信の通知が届かず、期限切れのジョブが与信済みにした注文")
    class RecoveredByJob {

        /**
         * 支払った注文の支払画面 ID
         */
        private String pageId;

        @BeforeEach
        void setUpRecovered() throws Exception {
            // 支払は済んだが、与信の成功の通知は届かない注文
            orderId = orders.placePendingOrder(variantId, 1);
            pageId = orders.openPaymentPage(orderId);
            gateway.markPaid(pageId);
        }

        @Test
        @DisplayName("AC-409-7 ジョブで受付になった注文は、出荷を登録できる")
        void shipsRecoveredOrder() throws Exception {
            // 準備：ジョブで受付にする
            recoverByJob();

            // 操作と確認：出荷済みになる
            ship("YAMATO", tracking).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("SHIPPED"));
        }

        @Test
        @DisplayName("AC-409-7 ジョブで受付になった注文の売上確定には、ジョブが取得した与信の参照を渡す")
        void capturesWithFetchedAuthorization() throws Exception {
            // 準備：ジョブで受付にする
            recoverByJob();

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 確認：この注文だけを、支払画面の与信の参照で売上確定した
            assertThat(gateway.captured()).containsExactly(orderId);
            assertThat(gateway.capturedAuthorizationRef(orderId)).contains(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("AC-409-8 決済サービスで与信が切れていた注文も受付になり、出荷の登録は与信期限切れの 409 で失敗する")
        void failsToShipRecoveredOrderWithExpiredAuthorization() throws Exception {
            // 準備：与信を切らしてから、ジョブで受付にする
            gateway.expireAuthorization(gateway.authorizationRefOf(pageId));
            recoverByJob();

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_EXPIRED"));

            // 確認：受付のまま、支払は与信期限切れ
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZATION_EXPIRED"));
        }

        @Test
        @DisplayName("AC-409-8 与信が切れていて出荷できなかった注文は、運営者がキャンセルできる")
        void cancelsRecoveredOrderWithExpiredAuthorization() throws Exception {
            // 準備：与信を切らしてからジョブで受付にし、出荷の登録に失敗する
            gateway.expireAuthorization(gateway.authorizationRefOf(pageId));
            recoverByJob();
            ship("YAMATO", tracking).andExpect(status().isConflict());

            // 確認（前提）：キャンセルの操作ができる
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.actions.cancel").value(true));

            // 操作：運営者がキャンセルする
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"reason": "在庫の不備"}"""))
                    .andExpect(status().isNoContent());

            // 確認：キャンセルになる
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        /**
         * 期限を過ぎてから、期限切れのジョブを実行し、注文が受付になったことを確認する。
         *
         * <p>ジョブが与信済みにすると、与信の成功のイベントがコミットの後に届き、リスナーが注文を受け付ける。
         * ここでは、リスナーと同じ処理を同期で呼んで受付にしてから進む（後から届くリスナーは、受付済みなので何も更新しない）。
         *
         * <p>確認すること（前提）：
         * <ul>
         *   <li>注文が「受付」</li>
         *   <li>支払が与信済み</li>
         * </ul>
         */
        private void recoverByJob() throws Exception {
            clock.setInstant(NOW.plus(Duration.ofHours(2)));
            PaymentJobs.expire(applicationContext);
            OrderPayments.accept(applicationContext, orderId);
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("売上確定の結果が分からず、照合のジョブが売上確定の状態を取得した注文")
    class ReconciledByJob {

        @Test
        @DisplayName("AC-423-9 照合で売上確定済みにした注文は、出荷の登録が 201 で、出荷済みになり、決済サービスへの売上確定は増えない")
        void shipsCapturedOrderWithoutCapturing() throws Exception {
            // 準備：売上確定の応答が届かず、照合で売上確定済みにする
            gateway.loseNextCaptureResponses(1);
            ship("YAMATO", tracking).andExpect(status().isBadGateway());
            reconcileUntil("CAPTURED");
            assertThat(gateway.captureCalls()).isEqualTo(1);

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 確認：出荷済みで、決済サービスへの売上確定は応答が届かなかった 1 回のまま
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.shipment.trackingNumber").value(tracking));
            assertThat(gateway.captureCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-423-5 決済サービスで売上を確定していなかった注文は、照合の後に出荷を登録すると 201 で、売上を確定する")
        void shipsNotCapturedOrderAndCaptures() throws Exception {
            // 準備：決済サービスに接続できず売上を確定せず、照合で売上確定を求めた時刻を削除する
            gateway.failNextCaptures(1);
            ship("YAMATO", tracking).andExpect(status().isBadGateway());
            var authorizationRef = gateway.authorizationRefOf(orders.paidPageId(orderId));
            reconcileUntil(() -> gateway.fetchCaptureStateCallsOf(authorizationRef) > 0);
            assertThat(gateway.captured()).isEmpty();

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 確認：出荷済みで、決済サービスで売上を確定した
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
            assertThat(gateway.captured()).containsExactly(orderId);
        }

        /**
         * 照合を始めるまでの時間が過ぎた後に、支払が指定の状態になるまで、期限切れのジョブを動かす。
         *
         * @param paymentStatus 支払の状態の名前
         */
        private void reconcileUntil(String paymentStatus) {
            reconcileUntil(() -> paymentStatusOf().equals(paymentStatus));
            assertThat(paymentStatusOf()).isEqualTo(paymentStatus);
        }

        private void reconcileUntil(BooleanSupplier done) {
            clock.setInstant(clock.instant().plus(properties.captureReconcileDelay()));
            PaymentJobs.expireUntil(applicationContext, done);
        }

        private String paymentStatusOf() {
            try {
                return JsonPath.read(mockMvc.perform(get("/api/admin/orders/" + orderId)).andReturn().getResponse()
                        .getContentAsString(), "$.payment.status");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    @DisplayName("登録できない注文と、重ねた登録")
    class Conflict {

        @Test
        @DisplayName("AC-170 「キャンセル申請中」の注文は 409 で、売上を確定しない")
        void rejectsCancellationRequested() throws Exception {
            // 準備：消費者がキャンセルを申請する
            requestCancellation();

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));

            // 確認：売上確定を呼ばない
            assertThat(gateway.captureCalls()).isZero();
        }

        @Test
        @DisplayName("AC-170 「キャンセル申請中」の注文は、申請を却下すると出荷できる")
        void shipsAfterRejectingRequest() throws Exception {
            // 準備：申請中に出荷の登録に失敗してから、申請を却下する
            requestCancellation();
            ship("YAMATO", tracking).andExpect(status().isConflict());
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"))
                    .andExpect(status().isNoContent());

            // 操作と確認：201
            ship("YAMATO", tracking).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-172 出荷済みの注文に重ねて登録すると 409")
        void rejectsShippedOrder() throws Exception {
            // 準備：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 操作と確認：別の配送業者で重ねると、409 の ORDER_NOT_SHIPPABLE
            ship("SAGAWA", ShippingTestData.trackingNumber()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));
        }

        @Test
        @DisplayName("AC-172 出荷済みの注文に重ねて登録しても、売上確定は 1 回だけで、最初の出荷が残る")
        void keepsFirstShipment() throws Exception {
            // 準備：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 操作：別の配送業者で重ねて登録する
            ship("SAGAWA", ShippingTestData.trackingNumber()).andExpect(status().isConflict());

            // 確認：売上確定は 1 回で、出荷は最初の配送業者のまま
            assertThat(gateway.captureCalls()).isEqualTo(1);
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.shipment.carrier").value("YAMATO"));
        }

        @Test
        @DisplayName("AC-172 売上確定の後に注文が受付でなくなっていたら 409 で、売上確定済みのまま、出荷は作られない")
        void rejectsOrderChangedDuringCapture() throws Exception {
            // 準備：売上確定の最中に、消費者がキャンセルを申請する
            requestCancellationDuringNextCapture();

            // 操作：出荷を登録する
            ship("YAMATO", tracking).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));

            // 確認：申請中で、支払は売上確定済み、出荷はない
            mockMvc.perform(get("/api/admin/orders/" + orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"))
                    .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                    .andExpect(jsonPath("$.shipment").doesNotExist());
        }

        @Test
        @DisplayName("AC-172 売上確定の後に受付でなくなった注文は、却下の後に登録し直すと、売上を確定し直さずに出荷できる")
        void shipsWithoutSecondCapture() throws Exception {
            // 準備：売上確定の最中に申請され、出荷の登録に失敗した後、申請を却下する
            requestCancellationDuringNextCapture();
            ship("YAMATO", tracking).andExpect(status().isConflict());
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/reject"))
                    .andExpect(status().isNoContent());

            // 操作：出荷を登録し直す
            ship("YAMATO", tracking).andExpect(status().isCreated());

            // 確認：売上確定は最初の 1 回だけ
            assertThat(gateway.captureCalls()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("入力")
    class Input {

        /**
         * 入力の検証で拒否される組。
         *
         * @return 場合の名前、配送業者、送り状番号、誤りの項目（項目ごとの誤りが付かないときは null）
         */
        static Stream<Arguments> invalidInputs() {
            return Stream.of(
                    Arguments.of("一覧にない配送業者", "KURONEKO", UNSAVED_TRACKING_NUMBER, null),
                    Arguments.of("空の送り状番号", "YAMATO", "", "trackingNumber"),
                    Arguments.of("欠けている送り状番号", "YAMATO", null, "trackingNumber"));
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("invalidInputs")
        @DisplayName("AC-173 配送業者と送り状番号の誤りは 400 で項目を示し、売上を確定しない")
        void validates(String name, String carrier, String trackingNumber, String field) throws Exception {
            // 操作と確認：400 で拒否され、売上を確定しない
            assertRejected(carrier, trackingNumber, field);
        }

        @Test
        @DisplayName("存在しない注文は 404")
        void notFound() throws Exception {
            // 操作と確認：ない注文 ID には 404
            mockMvc.perform(post("/api/admin/orders/" + UUID.randomUUID() + "/shipment")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"carrier": "YAMATO", "trackingNumber": "%s"}""".formatted(UNSAVED_TRACKING_NUMBER)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("送り状番号の形")
    class TrackingNumberFormat {

        /**
         * 送り状番号の形の誤り。
         *
         * @return 場合の名前と送り状番号
         */
        static Stream<Arguments> malformedTrackingNumbers() {
            return Stream.of(
                    Arguments.of("数字とハイフン以外を含む送り状番号", "ABC-123"),
                    Arguments.of("31 文字の送り状番号", "1".repeat(31)),
                    Arguments.of("数字を含まない送り状番号", "---"));
        }

        @Test
        @DisplayName("AC-167 上限の 30 文字の送り状番号で出荷を登録できる")
        void acceptsTrackingNumberAtMax() throws Exception {
            // 準備：テストごとの 12 桁の後に 18 桁を加えた、30 文字の送り状番号
            var atMax = tracking + "1".repeat(18);

            // 操作と確認：30 文字の送り状番号は 201
            ship("YAMATO", atMax).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.trackingNumber").value(atMax));
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("malformedTrackingNumbers")
        @DisplayName("送り状番号が、数字を 1 つ以上含む、数字とハイフンの 30 文字までの形でなければ 400 で項目を示し、売上を確定しない")
        void rejectsMalformedTrackingNumber(String name, String trackingNumber) throws Exception {
            // 操作と確認：400 で送り状番号の誤りを示し、売上を確定しない
            assertRejected("YAMATO", trackingNumber, "trackingNumber");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 出荷の登録が入力の誤りで拒否されることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>400</li>
     *   <li>エラーコードが INVALID_REQUEST</li>
     *   <li>項目を指定したときは、最初の項目ごとの誤りがその項目。null のときは、項目ごとの誤りが付かない</li>
     *   <li>売上確定を呼ばない</li>
     * </ul>
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（null なら本文に含めない）
     * @param field          誤りの項目（null なら項目ごとの誤りが付かない）
     */
    private void assertRejected(String carrier, String trackingNumber, String field) throws Exception {
        var body = trackingNumber == null
                ? """
                {"carrier": "%s"}""".formatted(carrier)
                : """
                {"carrier": "%s", "trackingNumber": "%s"}""".formatted(carrier, trackingNumber);

        var result = mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        if (field == null) {
            // 一覧にない値は、本文を読み取れない誤りなので、項目ごとの誤りは付かない
            result.andExpect(jsonPath("$.errors").doesNotExist());
        } else {
            result.andExpect(jsonPath("$.errors[0].field").value(field));
        }

        assertThat(gateway.captureCalls()).isZero();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 出荷を登録する。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 応答
     */
    private ResultActions ship(String carrier, String trackingNumber) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"carrier": "%s", "trackingNumber": "%s"}""".formatted(carrier, trackingNumber)));
    }

    /**
     * 消費者がキャンセルを申請する。
     */
    private void requestCancellation() throws Exception {
        mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
    }

    /**
     * 次の売上確定の最中に、消費者がキャンセルを申請するようにする。
     */
    private void requestCancellationDuringNextCapture() {
        gateway.duringNextCapture(() -> {
            try {
                requestCancellation();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
