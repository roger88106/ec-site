package com.ecsite.module.payment.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.PaymentTestData;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 決済の API の統合テスト
 *
 * <p>決済サービスは mock。支払はトランザクションを確定させて保存するので、テストごとに別の注文 ID を使う。
 */
@IntegrationTest
@RecordApplicationEvents
class PaymentApiTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 支払の期限（注文日時 ＋ 30 分）
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-07T16:00:00Z");

    /**
     * 支払の額
     */
    private static final Money AMOUNT = Money.of(6940);

    // ============================== Bean ==============================

    /**
     * 確認する決済の API
     */
    @Autowired
    PaymentApi paymentApi;

    /**
     * テスト用の決済サービス
     */
    @Autowired
    TestPaymentGateway gateway;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 支払の行を数える DB
     */
    @Autowired
    DSLContext dsl;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * 支払を進める補助
     */
    PaymentTestData payments;

    @BeforeEach
    void setUp() {
        payments = new PaymentTestData(paymentApi, gateway);
        clock.setInstant(ORDERED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("支払の作成")
    class Creation {

        @Test
        @DisplayName("BR-15、16 注文の支払は、未払いで、期限を注文日時の 30 分後として作られる")
        void createsUnpaidPayment() {
            // 準備：支払のない注文
            var orderId = UUID.randomUUID();

            // 操作：支払を作る
            paymentApi.create(orderId, AMOUNT, ORDERED_AT);

            // 確認：未払いで、期限は注文日時の 30 分後
            assertThat(paymentApi.find(List.of(orderId))).containsExactly(
                    new PaymentSummary(orderId, PaymentSummary.Status.UNPAID, DEADLINE));
        }

        @Test
        @DisplayName("BR-15、16 同じ注文で 2 回作っても、支払は 1 つだけ")
        void createsOnce() {
            // 準備：支払のない注文
            var orderId = UUID.randomUUID();

            // 操作：同じ注文で 2 回作る
            paymentApi.create(orderId, AMOUNT, ORDERED_AT);
            paymentApi.create(orderId, AMOUNT, ORDERED_AT);

            // 確認：支払の行が 1 つで、読める支払も 1 つ
            assertThat(dsl.fetchCount(PAYMENT_, PAYMENT_.ORDER_ID.eq(orderId))).isEqualTo(1);
            assertThat(paymentApi.find(List.of(orderId))).containsExactly(
                    new PaymentSummary(orderId, PaymentSummary.Status.UNPAID, DEADLINE));
        }
    }

    @Nested
    @DisplayName("支払画面")
    class Page {

        @Test
        @DisplayName("AC-152 支払画面の URL を何度求めても、開いている同じ画面を返す")
        void returnsSamePage() {
            // 準備：未払いの支払
            var orderId = payments.created(ORDERED_AT);

            // 操作：URL を 2 回求める
            var first = paymentApi.pageUrl(orderId);
            var second = paymentApi.pageUrl(orderId);

            // 確認：同じ画面
            assertThat(first).isPresent();
            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("AC-152 支払画面の URL を何度求めても、決済サービスに画面を再作成させない")
        void opensPageOnce() {
            // 準備：未払いの支払
            var orderId = payments.created(ORDERED_AT);

            // 操作：URL を 2 回求める
            paymentApi.pageUrl(orderId);
            paymentApi.pageUrl(orderId);

            // 確認：決済サービスで作った画面は 1 つ
            assertThat(gateway.openedPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("BR-16 支払の期限の後は、支払画面の URL を返さず、決済サービスに画面を作らせない")
        void noPageAfterDeadline() {
            // 準備：期限まで時計を進める
            var orderId = payments.created(ORDERED_AT);
            clock.setInstant(DEADLINE);

            // 操作：支払画面の URL を求める
            var url = paymentApi.pageUrl(orderId);

            // 確認：URL を返さず、決済サービスで画面を作らない
            assertThat(url).isEmpty();
            assertThat(gateway.openedPages()).isZero();
        }

        @Test
        @DisplayName("支払のない注文には、支払画面の URL を返さない")
        void noPageForUnknownOrder() {
            // 操作と確認：支払のない注文の URL を求めると、ない
            assertThat(paymentApi.pageUrl(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("与信の成功の通知")
    class AuthorizationNotification {

        @Test
        @DisplayName("AC-146 与信の成功の通知で、支払は与信済みになる")
        void authorizesOnNotification() {
            // 準備：支払画面を開いた未払いの支払
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);

            // 操作：支払う（決済サービスから通知が届く）
            gateway.pay(pageId);

            // 確認：与信済み
            assertThat(payments.statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZED);
        }

        @Test
        @DisplayName("AC-146 与信の成功の通知で、この注文の与信の成功のイベントが 1 件出る")
        void publishesAuthorizedEvent() {
            // 準備：支払画面を開いた未払いの支払
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);

            // 操作：支払う（決済サービスから通知が届く）
            gateway.pay(pageId);

            // 確認：与信の成功のイベントが、この注文に 1 件
            assertThat(authorizedEvents(orderId)).singleElement()
                    .satisfies(event -> assertThat(event.orderId()).isEqualTo(orderId));
        }

        @Test
        @DisplayName("AC-146 与信の成功の通知では、売上を確定しない")
        void doesNotCaptureOnNotification() {
            // 準備：支払画面を開いた未払いの支払
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);

            // 操作：支払う（決済サービスから通知が届く）
            gateway.pay(pageId);

            // 確認：決済サービスの売上確定を呼ばない
            assertThat(gateway.captureCallsOf(orderId)).isZero();
        }

        @Test
        @DisplayName("BR-17 与信の成功の通知が重ねて届いても、イベントは 1 回だけ")
        void ignoresDuplicateNotification() {
            // 準備：通知が 1 回届いている
            var orderId = payments.created(ORDERED_AT);
            var pageId = payments.openPage(orderId);
            gateway.pay(pageId);

            // 操作：同じ通知がもう一度届く
            gateway.notifyAuthorized(pageId);

            // 確認：与信の成功のイベントは 1 件のまま
            assertThat(authorizedEvents(orderId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("支払の状態の読み取り")
    class Summaries {

        @Test
        @DisplayName("支払の状態を、注文 ID でまとめて読める。支払のない注文は含まない")
        void findsSummaries() {
            // 準備：未払いと与信済みの支払
            var unpaid = payments.created(ORDERED_AT);
            var authorized = payments.created(ORDERED_AT);
            payments.pay(authorized);

            // 操作：支払のない注文を加えて読む
            var summaries = paymentApi.find(List.of(unpaid, authorized, UUID.randomUUID()));

            // 確認：支払のある 2 件だけ
            assertThat(summaries).extracting(PaymentSummary::orderId, PaymentSummary::status).containsExactlyInAnyOrder(
                    tuple(unpaid, PaymentSummary.Status.UNPAID),
                    tuple(authorized, PaymentSummary.Status.AUTHORIZED));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の与信の成功のイベントを返す。<br />
     * 他のテストのイベントを含めないように、注文 ID で絞る。
     *
     * @param orderId 注文 ID
     * @return イベント
     */
    private List<PaymentAuthorized> authorizedEvents(UUID orderId) {
        return events.stream(PaymentAuthorized.class).filter(event -> event.orderId().equals(orderId)).toList();
    }
}
