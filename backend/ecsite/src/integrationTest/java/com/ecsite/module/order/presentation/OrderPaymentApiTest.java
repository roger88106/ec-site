package com.ecsite.module.order.presentation;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.application.PaymentRequests;
import com.ecsite.module.payment.application.job.PaymentJobs;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文と支払の受け入れテスト
 *
 * <p>注文の API から見た、支払待ちの注文の作成、与信の反映、支払の期限切れ、支払の状態の表示、キャンセルによる与信の取消を確認する。
 * 支払の状態の遷移そのものは決済の統合テスト（{@code PaymentVoidTest} など）、支払の期限の判定の境界は
 * {@code PaymentExpiryJobTest} が確認する。
 *
 * <p>決済のイベントはコミットの後に非同期で届くので、コミットして処理を待つ。作ったデータは消さないので、
 * 会員と税区分の名前はテストごとに変え、確認は自分の注文だけにする。決済サービスは mock。支払の期限は、
 * 時計を進めて期限切れのジョブを呼んで確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
class OrderPaymentApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。商品を公開するたびに 1 分進むので、注文日時はこれより後になる。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 準備で入荷する在庫数
     */
    private static final int STOCKED = 5;

    /**
     * 支払の期限（注文日時からの時間）
     */
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    /**
     * 2 個の注文の総額（送料を含む）
     *
     * <p>{@link #stockedVariant} の商品（税抜 1,000 円、税率 10% で税込 1,100 円）× 2 個の 2,200 円に、
     * 送料の既定の 1,000 円（税込）を加えた額。
     */
    private static final Money TOTAL_OF_TWO = Money.of(3200);

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

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * このテストの会員
     *
     * <p>巻き戻さないので、他のテストとカートを共有しないよう、テストごとに変える。
     */
    private TestTokens.Builder owner;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 注文する商品のバリアント ID（在庫は {@link #STOCKED}）
     */
    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        taxCategoryId = new CatalogTestData(mockMvc, clock).taxCategory("支払-" + UUID.randomUUID().toString().substring(0, 8), "10");
        variantId = stockedVariant("支払の商品");
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer());
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("US-31 注文の代金を支払う")
    class Pay {

        @Test
        @DisplayName("AC-143 注文すると「支払待ち」で作られ、支払の期限までは支払える")
        void placesPendingOrder() throws Exception {
            // 操作：支払わずに注文する
            var orderId = pending(2);

            // 確認：支払待ちで、支払の期限があり、支払えるがキャンセルの申請はできない
            assertAwaitingPayment(orderId, deadlineOf(orderId));
        }

        @Test
        @DisplayName("AC-144 「支払待ち」の注文を作ると、注文の数量だけ在庫数が減る")
        void reducesStock() throws Exception {
            // 操作：2 個を支払わずに注文する
            pending(2);

            // 確認：在庫数が 2 減る
            expectQuantity(variantId, STOCKED - 2);
        }

        @Test
        @DisplayName("AC-143 「支払待ち」の注文を作ると、未払いの支払が作られる")
        void createsUnpaidPayment() throws Exception {
            // 操作：支払わずに注文する
            var orderId = pending(2);

            // 確認：管理画面の支払の状態が未払い
            admin(orderId).andExpect(jsonPath("$.payment.status").value("UNPAID"));
        }

        @Test
        @DisplayName("AC-144 「支払待ち」の注文を作っても、カートは変わらない")
        void keepsCart() throws Exception {
            // 操作：支払わずに注文する
            pending(2);

            // 確認：注文した品目がカートに残る
            assertThat(cartVariantIds()).contains(variantId.toString());
        }

        @Test
        @DisplayName("AC-146 与信が成功すると「受付」になり、支払の期限と支払の操作がなくなる")
        void acceptsOnAuthorization() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(2);

            // 操作：決済サービスで支払い、受付になるのを待つ
            gateway.pay(orders.openPaymentPage(orderId));
            orders.awaitStatus(orderId, "ACCEPTED");

            // 確認：支払の期限がなく、支払えず、キャンセルを申請できる
            store(orderId)
                    .andExpect(jsonPath("$.paymentDeadline").value(nullValue()))
                    .andExpect(jsonPath("$.actions.pay").value(false))
                    .andExpect(jsonPath("$.actions.requestCancellation").value(true));
        }

        @Test
        @DisplayName("AC-146 与信が成功しても、売上は確定せず、支払は与信済みになる")
        void keepsAuthorizedWithoutCapture() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(2);

            // 操作：決済サービスで支払い、受付になるのを待つ
            gateway.pay(orders.openPaymentPage(orderId));
            orders.awaitStatus(orderId, "ACCEPTED");

            // 確認：支払が与信済み
            admin(orderId).andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));
        }

        @Test
        @DisplayName("AC-147 与信が成功すると、注文した品目だけがカートから除かれ、注文の後に追加した品目は残る")
        void removesOnlyOrderedItem() throws Exception {
            // 準備：注文の後に、別の品目をカートに追加する
            var orderId = pending(2);
            var addedLater = stockedVariant("後から追加した商品");
            orders.add(orders.cart(), addedLater, 1);

            // 操作：決済サービスで支払い、受付になるのを待つ
            gateway.pay(orders.openPaymentPage(orderId));
            orders.awaitStatus(orderId, "ACCEPTED");

            // 確認：除く処理を待つと、後から追加した品目だけが残る
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(cartVariantIds()).containsExactly(addedLater.toString()));
        }

        @Test
        @DisplayName("AC-147 品目が複数の注文でも、与信が成功すると、注文した品目がすべてカートから除かれる")
        void removesAllOrderedItems() throws Exception {
            // 準備：2 品目の注文
            var other = stockedVariant("一緒に注文する商品");
            orders.add(orders.cart(), other, 1);
            var orderId = pending(1);

            // 操作：決済サービスで支払い、受付になるのを待つ
            gateway.pay(orders.openPaymentPage(orderId));
            orders.awaitStatus(orderId, "ACCEPTED");

            // 確認：除く処理を待つと、カートが空になる
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(cartVariantIds()).isEmpty());
        }

        @Test
        @DisplayName("AC-151 持ち主でない会員は、支払画面を開けない")
        void otherMemberCannotOpenPage() throws Exception {
            // 準備：支払待ちの注文と、別の会員
            var orderId = pending(1);
            var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");

            // 操作と確認：注文がないものとして 404
            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(other.bearer()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-151 ログインしていなければ、支払画面を開けない")
        void anonymousCannotOpenPage() throws Exception {
            // 準備：支払待ちの注文
            var orderId = pending(1);

            // 操作と確認：401
            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-151 支払の期限の直前なら、支払画面を開ける")
        void opensPageJustBeforeDeadline() throws Exception {
            // 準備：支払の期限の 1 秒前にする
            var orderId = pending(1);
            clock.setInstant(deadlineOf(orderId).minusSeconds(1));

            // 操作と確認：開ける
            openPage(orderId).andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-151 「受付」の注文は、支払画面を開けない")
        void acceptedOrderIsNotPayable() throws Exception {
            // 準備：支払って受付にする
            var orderId = pending(1);
            orders.pay(orderId);

            // 操作と確認：支払えないことのエラーで 409
            openPage(orderId).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_PAYABLE"));
        }

        @Test
        @DisplayName("AC-151 「受付」の注文の支払画面を拒否したときは、決済サービスに支払画面を再作成しない")
        void createsNoPageForAcceptedOrder() throws Exception {
            // 準備：支払画面を開いて支払い、受付にする（支払画面は 1 回作られる）
            var orderId = pending(1);
            orders.pay(orderId);

            // 操作：もう一度支払画面を開いて拒否される
            openPage(orderId).andExpect(status().isConflict());

            // 確認：決済サービスの支払画面は、支払ったときの 1 回だけ
            assertThat(gateway.openedPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-151 支払の期限の後の注文は、支払画面を開けない")
        void expiredOrderIsNotPayable() throws Exception {
            // 準備：支払の期限にする
            var orderId = pending(1);
            clock.setInstant(deadlineOf(orderId));

            // 操作と確認：支払えないことのエラーで 409
            openPage(orderId).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_PAYABLE"));
        }

        @Test
        @DisplayName("AC-151 支払の期限の後の注文は、詳細で支払の操作ができない")
        void expiredOrderOffersNoPayment() throws Exception {
            // 準備：支払の期限にする
            var orderId = pending(1);
            clock.setInstant(deadlineOf(orderId));

            // 操作と確認：支払のフラグが立たない
            store(orderId).andExpect(jsonPath("$.actions.pay").value(false));
        }

        @Test
        @DisplayName("AC-295 決済サービスに接続できなければ、502 と、やり直せることを示すメッセージを返す")
        void gatewayUnavailable() throws Exception {
            // 準備：決済サービスが次の画面の作成に失敗する
            var orderId = pending(1);
            gateway.failNextOpens(1);

            // 操作と確認：502 と、やり直しを促すメッセージ
            openPage(orderId)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_GATEWAY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value(containsString("やり直して")));
        }

        @Test
        @DisplayName("AC-295 決済サービスに接続できなかった後も、やり直せば支払画面を開ける")
        void opensPageOnRetry() throws Exception {
            // 準備：決済サービスが 1 回だけ画面の作成に失敗し、最初の要求が 502 になる
            var orderId = pending(1);
            gateway.failNextOpens(1);
            openPage(orderId).andExpect(status().isBadGateway());

            // 操作と確認：やり直すと開ける
            openPage(orderId).andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-143 支払画面を開くと、決済サービスに、注文の総額（送料を含む）を与信の額として渡す")
        void opensPageWithOrderTotal() throws Exception {
            // 準備：2 個を支払わずに注文する
            var orderId = pending(2);

            // 確認（前提）：注文の総額が、送料を含む額
            var total = dsl.select(SALES_ORDER.TOTAL_AMOUNT).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                    .fetchOne(SALES_ORDER.TOTAL_AMOUNT);
            assertThat(total).isEqualTo(TOTAL_OF_TWO.yen());

            // 操作：支払画面を開く
            openPage(orderId).andExpect(status().isOk());

            // 確認：与信の額が総額
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.OPEN_PAGE, orderId)).containsExactly(TOTAL_OF_TWO);
        }

        @Test
        @DisplayName("AC-152 支払画面を何度開いても、同じ画面になり、決済サービスの画面は 1 回だけ作られる")
        void reusesPage() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作：支払画面を 2 回開く
            var first = openedPageId(orderId);
            var second = openedPageId(orderId);

            // 確認：同じ画面で、作成は 1 回
            assertThat(second).isEqualTo(first);
            assertThat(gateway.openedPages()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("US-32 支払の期限が切れた注文を取り消す")
    class Expire {

        @Test
        @DisplayName("AC-153 支払の期限の前は、期限切れのジョブを動かしても「支払待ち」のまま")
        void keepsOrderBeforeDeadline() throws Exception {
            // 準備：支払の期限の 1 秒前にする
            var orderId = pending(2);
            clock.setInstant(deadlineOf(orderId).minusSeconds(1));

            // 操作：期限切れのジョブを動かす
            PaymentJobs.expire(applicationContext);

            // 確認：支払待ちのまま
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-153 支払の期限を過ぎると、期限切れのジョブでキャンセルになる")
        void cancelsAtDeadline() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(2);

            // 操作：支払の期限に期限切れのジョブを動かす
            expireAtDeadline(orderId);

            // 確認：キャンセルになる
            orders.awaitStatus(orderId, "CANCELLED");
        }

        @Test
        @DisplayName("AC-155 期限切れのキャンセルは、種類が「支払期限切れ」で、店舗と管理の注文の詳細に出る")
        void showsPaymentExpiredKind() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(2);

            // 操作：期限切れでキャンセルする
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 確認：店舗と管理の詳細に、支払期限切れのキャンセルとして出る
            assertCancelledByExpiry(orderId);
        }

        @Test
        @DisplayName("AC-160 期限切れでキャンセルした注文の詳細の支払の状態は、支払われなかったことを示す")
        void showsNotPaid() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(2);

            // 操作：期限切れでキャンセルする
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 確認：支払の状態が NOT_PAID
            admin(orderId).andExpect(jsonPath("$.payment.status").value("NOT_PAID"));
        }

        @Test
        @DisplayName("AC-153 期限切れでキャンセルすると、明細の数量だけ在庫数が戻る")
        void restoresStock() throws Exception {
            // 準備：2 個を支払わずに注文する
            var orderId = pending(2);

            // 操作：期限切れでキャンセルする
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 確認：入荷した数に戻る
            expectQuantity(variantId, STOCKED);
        }

        @Test
        @DisplayName("AC-107 期限切れのキャンセルで戻した在庫は、調整履歴に理由「注文のキャンセル」で残る")
        void recordsRestoreInHistory() throws Exception {
            // 準備：2 個を支払わずに注文する
            var orderId = pending(2);

            // 操作：期限切れでキャンセルする
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 確認：最新の調整が、注文のキャンセルによる 2 個の戻し
            mockMvc.perform(get("/api/admin/inventory/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"))
                    .andExpect(jsonPath("$.items[0].delta").value(2));
        }

        @Test
        @DisplayName("AC-157 期限切れでキャンセルした後に与信の成功が届いたら、その与信を取り消す")
        void voidsLateAuthorization() throws Exception {
            // 準備：支払画面を開いた後に、期限切れでキャンセルする
            var orderId = pending(1);
            var pageId = orders.openPaymentPage(orderId);
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 操作：遅れて与信の成功を届け、支払が与信の取消済みになるのを待つ
            gateway.notifyAuthorized(pageId);
            awaitPaymentStatus(orderId, "VOIDED");

            // 確認：決済サービスで、届いた与信だけが取り消された
            assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("AC-157 期限切れでキャンセルした後に与信の成功が届いても、注文はキャンセルのまま")
        void keepsCancelledOnLateAuthorization() throws Exception {
            // 準備：支払画面を開いた後に、期限切れでキャンセルする
            var orderId = pending(1);
            var pageId = orders.openPaymentPage(orderId);
            expireAtDeadline(orderId);
            orders.awaitStatus(orderId, "CANCELLED");

            // 操作：遅れて与信の成功を届け、その処理（与信の取消）が終わるのを待つ
            gateway.notifyAuthorized(pageId);
            awaitPaymentStatus(orderId, "VOIDED");

            // 確認：キャンセルのまま
            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("AC-409-1 与信の成功の通知が届かなくても、期限切れのジョブが与信を取得し、支払は与信済み、注文は受付になる")
        void acceptsOrderWithoutNotification() throws Exception {
            // 準備：決済サービスでは支払済みだが、通知は届けずに支払の期限にする
            var orderId = pending(1);
            gateway.markPaid(orders.openPaymentPage(orderId));
            clock.setInstant(deadlineOf(orderId));

            // 操作：期限切れのジョブを動かす
            PaymentJobs.expire(applicationContext);

            // 確認：注文は受付、支払は与信済み
            orders.awaitStatus(orderId, "ACCEPTED");
            admin(orderId).andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));
        }

        @Test
        @DisplayName("AC-147 期限切れのジョブで受付になった注文も、注文した品目がカートから除かれる")
        void removesItemsWithoutNotification() throws Exception {
            // 準備：決済サービスでは支払済みだが、通知は届けずに支払の期限にする
            var orderId = pending(1);
            gateway.markPaid(orders.openPaymentPage(orderId));
            clock.setInstant(deadlineOf(orderId));

            // 操作：期限切れのジョブを動かし、受付になるのを待つ
            PaymentJobs.expire(applicationContext);
            orders.awaitStatus(orderId, "ACCEPTED");

            // 確認：除く処理を待つと、カートが空になる
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(cartVariantIds()).isEmpty());
        }

        @Test
        @DisplayName("AC-158 「支払待ち」の注文の詳細は、運営者のキャンセルの操作がない")
        void offersNoOperatorCancellation() throws Exception {
            // 操作：支払わずに注文する
            var orderId = pending(1);

            // 確認：キャンセルのフラグが立たない
            admin(orderId).andExpect(jsonPath("$.actions.cancel").value(false));
        }

        @Test
        @DisplayName("AC-158 「支払待ち」の注文は、運営者もキャンセルできず、支払待ちのまま")
        void operatorCannotCancelPending() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作と確認：支払待ちであることのエラーで 400
            cancelByOperator(orderId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("AWAITING_PAYMENT"));

            // 確認：支払待ちのまま
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-158 「支払待ち」の注文は、消費者もキャンセルを申請できず、支払待ちのまま")
        void consumerCannotRequestCancellationOfPending() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作と確認：申請できないことのエラーで 400
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));

            // 確認：支払待ちのまま
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }
    }

    @Nested
    @DisplayName("US-33 支払の状態を確認する")
    class PaymentStatusView {

        @Test
        @DisplayName("AC-161 管理画面の一覧を「支払待ち」で絞り込むと、支払待ちの注文が出る")
        void listsPendingOrder() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作と確認：自分の注文番号と「支払待ち」で絞ると出る（他のテストの注文が混ざらないよう、注文番号でも絞る）
            listPending(orderId).andExpect(jsonPath("$.items[*].id").value(hasItem(orderId.toString())));
        }

        @Test
        @DisplayName("AC-161 管理画面の一覧を「支払待ち」で絞り込むと、受付の注文は出ない")
        void excludesAcceptedOrder() throws Exception {
            // 準備：支払って受付にする
            var orderId = pending(1);
            orders.pay(orderId);

            // 操作と確認：自分の注文番号と「支払待ち」で絞ると出ない
            listPending(orderId).andExpect(jsonPath("$.items[*].id").value(not(hasItem(orderId.toString()))));
        }

        @Test
        @DisplayName("AC-160 管理画面の一覧の行に、支払待ちの注文の支払の状態（未払い）がある")
        void listsUnpaidStatus() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作と確認：行の支払の状態が未払い
            listPending(orderId)
                    .andExpect(jsonPath("$.items[?(@.id == '" + orderId + "')].paymentStatus").value(hasItem("UNPAID")));
        }

        @Test
        @DisplayName("AC-160 管理画面の一覧の行に、受付の注文の支払の状態（与信済み）がある")
        void listsAuthorizedStatus() throws Exception {
            // 準備：支払って受付にする
            var orderId = pending(1);
            orders.pay(orderId);

            // 操作と確認：自分の注文番号で絞ると、行の支払の状態が与信済み
            mockMvc.perform(get("/api/admin/orders").param("orderNumber", orderNumber(orderId)))
                    .andExpect(jsonPath("$.items[?(@.id == '" + orderId + "')].paymentStatus").value(hasItem("AUTHORIZED")));
        }

        @Test
        @DisplayName("AC-160 管理画面の詳細に、支払の状態と支払の期限がある")
        void showsPaymentInDetail() throws Exception {
            // 準備：支払わずに注文する
            var orderId = pending(1);

            // 操作と確認：未払いで、期限は注文日時の 30 分後
            admin(orderId)
                    .andExpect(jsonPath("$.payment.status").value("UNPAID"))
                    .andExpect(jsonPath("$.payment.deadline").value(deadlineOf(orderId).toString()));
        }
    }

    @Nested
    @DisplayName("US-34 キャンセルした注文の与信を取り消す")
    class VoidAuthorization {

        @Test
        @DisplayName("AC-162 運営者のキャンセルで、与信が取り消される")
        void voidsOnOperatorCancel() throws Exception {
            // 準備：支払って受付にする
            var orderId = accepted();

            // 操作：運営者がキャンセルする
            cancelByOperator(orderId).andExpect(status().isNoContent());

            // 確認（前提）：キャンセルになる
            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));

            // 確認：支払が与信の取消済みになる
            awaitPaymentStatus(orderId, "VOIDED");
        }

        @Test
        @DisplayName("AC-162 申請の承認によるキャンセルでも、与信が取り消される")
        void voidsOnApproval() throws Exception {
            // 準備：支払って受付にし、キャンセルを申請する
            var orderId = accepted();
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNoContent());

            // 操作：申請を承認する
            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve"))
                    .andExpect(status().isNoContent());

            // 確認：支払が与信の取消済みになる
            awaitPaymentStatus(orderId, "VOIDED");
        }

        @Test
        @DisplayName("AC-163 決済サービスで与信の取消に失敗しても、キャンセルは成立する")
        void cancelsEvenIfVoidFails() throws Exception {
            // 準備：受付の注文で、決済サービスが次の取消に失敗する
            var orderId = accepted();
            gateway.failNextVoids(1);

            // 操作：運営者がキャンセルする
            cancelByOperator(orderId).andExpect(status().isNoContent());

            // 確認：取消の結果を待たずにキャンセルになる
            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));

            // 確認：取消が失敗した後もキャンセルのまま
            asyncEvents.awaitFailed(PaymentRequests.voidAuthorization(dsl, orderId));
            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("AC-165 与信の取消に失敗している間は、管理画面に「与信の取消待ち」と出る")
        void showsVoidPending() throws Exception {
            // 準備：受付の注文で、決済サービスが次の取消に失敗する
            var orderId = accepted();
            gateway.failNextVoids(1);

            // 操作：運営者がキャンセルし、取消の依頼のリスナーが失敗するのを待つ
            cancelByOperator(orderId).andExpect(status().isNoContent());
            asyncEvents.awaitFailed(PaymentRequests.voidAuthorization(dsl, orderId));

            // 確認：支払の状態が与信の取消待ち
            assertThat(paymentStatus(orderId)).isEqualTo("VOID_PENDING");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文が支払待ちで、支払える状態であることを、店舗の API で確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が「支払待ち」</li>
     *   <li>支払の期限が、注文日時の 30 分後</li>
     *   <li>支払える</li>
     *   <li>キャンセルを申請できない</li>
     * </ul>
     *
     * @param orderId  注文 ID
     * @param deadline 期待する支払の期限（注文日時は商品の公開で進んだ時計で決まり、定数にできないので、注文から求める）
     */
    private void assertAwaitingPayment(UUID orderId, Instant deadline) throws Exception {
        store(orderId)
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentDeadline").value(deadline.toString()))
                .andExpect(jsonPath("$.actions.pay").value(true))
                .andExpect(jsonPath("$.actions.requestCancellation").value(false));
    }

    /**
     * 注文が支払期限切れでキャンセルされたことを、店舗と管理の API で確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>店舗の詳細のキャンセルの種類が「支払期限切れ」</li>
     *   <li>管理の詳細のキャンセルの種類が「支払期限切れ」</li>
     *   <li>管理の詳細のキャンセルの理由がない</li>
     *   <li>管理の詳細のキャンセルした運営者がいない</li>
     * </ul>
     *
     * @param orderId 注文 ID
     */
    private void assertCancelledByExpiry(UUID orderId) throws Exception {
        store(orderId).andExpect(jsonPath("$.cancellation.kind").value("PAYMENT_EXPIRED"));
        admin(orderId)
                .andExpect(jsonPath("$.cancellation.kind").value("PAYMENT_EXPIRED"))
                .andExpect(jsonPath("$.cancellation.reason").value(nullValue()))
                .andExpect(jsonPath("$.cancellation.cancelledBy").value(nullValue()));
    }

    /**
     * 会員として、店舗の API で注文の詳細を読み、200 を確認する。
     *
     * @param orderId 注文 ID
     * @return 応答
     */
    private ResultActions store(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andExpect(status().isOk());
    }

    /**
     * 管理の API で注文の詳細を読み、200 を確認する。
     *
     * @param orderId 注文 ID
     * @return 応答
     */
    private ResultActions admin(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(status().isOk());
    }

    /**
     * 管理の一覧を、「支払待ち」と注文の注文番号で絞り込み、200 を確認する。
     *
     * @param orderId 注文 ID（他のテストの注文が混ざらないよう、この注文の注文番号で絞る）
     * @return 応答
     */
    private ResultActions listPending(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/admin/orders").param("status", "PENDING_PAYMENT").param("orderNumber", orderNumber(orderId)))
                .andExpect(status().isOk());
    }

    /**
     * 会員のカートの品目のバリアント ID を読む。
     *
     * @return バリアント ID の一覧
     */
    private List<String> cartVariantIds() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/carts/" + orders.cart()).with(owner.bearer())).andReturn().getResponse().getContentAsString(),
                "$.items[*].variantId");
    }

    /**
     * 管理の API で、注文の支払の状態を読む。
     *
     * @param orderId 注文 ID
     * @return 支払の状態
     */
    private String paymentStatus(UUID orderId) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/admin/orders/" + orderId)).andReturn().getResponse().getContentAsString(),
                "$.payment.status");
    }

    /**
     * 注文の支払の状態が、指定の状態になるのを待つ。
     *
     * @param orderId  注文 ID
     * @param expected 待つ支払の状態
     */
    private void awaitPaymentStatus(UUID orderId, String expected) {
        await().atMost(TIMEOUT).until(() -> paymentStatus(orderId).equals(expected));
    }


    /**
     * バリアントの在庫数を確認する。
     *
     * @param variant  バリアント ID
     * @param quantity 期待する在庫数
     */
    private void expectQuantity(UUID variant, int quantity) throws Exception {
        InventoryTestData.expectQuantity(mockMvc, variant, quantity);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 商品を作って公開し、在庫を {@link #STOCKED} にする。
     *
     * @param name 商品名
     * @return バリアント ID
     */
    private UUID stockedVariant(String name) throws Exception {
        return inventory.stockedVariant(new CatalogTestData(mockMvc, clock), taxCategoryId, name, STOCKED);
    }

    /**
     * 支払わずに注文する。
     *
     * @param quantity 数量
     * @return 注文 ID
     */
    private UUID pending(int quantity) throws Exception {
        return orders.placePendingOrder(variantId, quantity);
    }

    /**
     * 注文して支払い、受付にする（与信の成功のイベントの処理を待つ）。
     *
     * @return 注文 ID
     */
    private UUID accepted() throws Exception {
        var orderId = pending(1);
        gateway.pay(orders.openPaymentPage(orderId));
        orders.awaitStatus(orderId, "ACCEPTED");
        return orderId;
    }

    /**
     * 時計を支払の期限にして、期限切れのジョブを動かす。
     *
     * @param orderId 注文 ID
     */
    private void expireAtDeadline(UUID orderId) throws Exception {
        clock.setInstant(deadlineOf(orderId));
        PaymentJobs.expire(applicationContext);
    }

    /**
     * 会員として、支払画面を開く要求を送る。
     *
     * @param orderId 注文 ID
     * @return 応答
     */
    private ResultActions openPage(UUID orderId) throws Exception {
        return mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(owner.bearer()));
    }

    /**
     * 会員として、支払画面を開き、200 を確認して、支払画面 ID（応答の URL の最後の部分）を返す。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    private String openedPageId(UUID orderId) throws Exception {
        var body = openPage(orderId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * 運営者のキャンセルを送る。
     *
     * @param orderId 注文 ID
     * @return 応答
     */
    private ResultActions cancelByOperator(UUID orderId) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "在庫の不備"}"""));
    }

    /**
     * 会員として、注文の注文番号を読む。
     *
     * @param orderId 注文 ID
     * @return 注文番号
     */
    private String orderNumber(UUID orderId) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andReturn().getResponse()
                .getContentAsString(), "$.orderNumber");
    }

    /**
     * 注文の支払の期限（注文日時 ＋ {@link #PAYMENT_WINDOW}）を求める。
     *
     * @param orderId 注文 ID
     * @return 支払の期限
     */
    private Instant deadlineOf(UUID orderId) throws Exception {
        String orderedAt = JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andReturn().getResponse()
                .getContentAsString(), "$.orderedAt");
        return Instant.parse(orderedAt).plus(PAYMENT_WINDOW);
    }
}
