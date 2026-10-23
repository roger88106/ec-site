package com.ecsite.module.payment.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 返金の統合テスト（T9108、AC-188、192、193、194、195、BR-21）。<br />
 * 返金はコミットの後に非同期で行われるので、結果を待つ。
 */
@IntegrationTest
@Import(TestClockConfig.class)
class PaymentRefundTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    PaymentApi paymentApi;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    MutableClock clock;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    DSLContext dsl;

    /**
     * 注文 ID → 支払画面 ID
     */
    private final Map<UUID, String> pageIds = new HashMap<>();

    @BeforeEach
    void setUp() {
        clock.setInstant(ORDERED_AT);
        gateway.reset();
    }

    @Test
    @DisplayName("AC-192、193 売上確定済みの支払を取り消すと返金待ちになり、コミットの後に決済サービスで返金されて、返金済みになる")
    void refundsCapturedPayment() {
        var orderId = captured();

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsExactly(orderId);
    }

    @Test
    @DisplayName("AC-192 返金で決済サービスに渡す額は、その注文の売上確定の額（総額）と同じ")
    void refundsCapturedAmount() {
        var orderId = captured();

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId)).containsExactly(Money.of(6940));
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId))
                .isEqualTo(gateway.amountsOf(TestPaymentGateway.Operation.CAPTURE, orderId));
    }

    @Test
    @DisplayName("AC-306 返金のリスナーは、データベースのトランザクションの外で決済サービスを呼ぶ")
    void refundsOutsideTransaction() {
        var orderId = captured();
        var transactionActive = new CopyOnWriteArrayList<Boolean>();
        gateway.onRefundOrVoid(() -> transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive()));

        paymentApi.cancel(orderId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(transactionActive).containsExactly(false);
    }

    @Test
    @DisplayName("AC-193、194 決済サービスが返金に失敗したら返金待ちのまま残り、未完了のイベントの再発行で返金される")
    void retriesFailedRefund() {
        var orderId = captured();
        gateway.failNextRefunds(1);

        paymentApi.cancel(orderId);

        asyncEvents.awaitFailed(PaymentRequests.of(dsl, orderId));
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUND_PENDING);
        assertThat(gateway.refunded()).isEmpty();

        // 再発行のジョブ（発行から 5 分より前のもの）と同じ
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsExactly(orderId);
    }

    @Test
    @DisplayName("AC-195 返金済みの支払を重ねて取り消しても、成功し、返金は 1 回のまま変わらない")
    void ignoresRepeatedCancel() {
        var orderId = captured();
        paymentApi.cancel(orderId);
        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.refunded()).hasSize(1);
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
    }

    @Test
    @DisplayName("AC-188 与信期限切れの支払を取り消しても、何もしない。返金も与信の取消も呼ばない")
    void cancelsAuthorizationExpiredWithoutRefund() {
        var orderId = authorized();
        gateway.expireAuthorization(gateway.authorizationRefOf(pageIdOf(orderId)));
        assertBusinessError(() -> paymentApi.capture(orderId), PaymentErrorCode.AUTHORIZATION_EXPIRED);

        paymentApi.cancel(orderId);

        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.refunded()).isEmpty();
        assertThat(gateway.voided()).isEmpty();
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.AUTHORIZATION_EXPIRED);
    }

    @Test
    @DisplayName("T9314 売上確定の最中に注文がキャンセルされたら、確定の反映で返金待ちになり、返金される。与信は取り消さない")
    void refundsWhenCancelledDuringCapture() {
        var orderId = authorized();
        // 決済サービスが確定した後、確定を反映する前に、注文のキャンセルで支払が与信の取消待ちになる
        gateway.duringNextCapture(() -> paymentApi.cancel(orderId));

        paymentApi.capture(orderId);

        await().atMost(TIMEOUT).until(() -> statusOf(orderId) == PaymentSummary.Status.REFUNDED);
        assertThat(gateway.refunded()).containsExactly(orderId);

        // 与信の取消の依頼は、再発行で何もせずに終わる
        clock.advance(Duration.ofMinutes(6));
        incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
        assertThat(gateway.voided()).isEmpty();
        assertThat(statusOf(orderId)).isEqualTo(PaymentSummary.Status.REFUNDED);
    }

    // ============================== 補助 ==============================

    private UUID created() {
        var orderId = UUID.randomUUID();
        paymentApi.create(orderId, Money.of(6940), ORDERED_AT);
        return orderId;
    }

    /**
     * 支払画面を開いて支払い、与信済みにする。
     */
    private UUID authorized() {
        var orderId = created();
        var url = paymentApi.pageUrl(orderId).orElseThrow();
        var pageId = url.substring(url.lastIndexOf('/') + 1);
        pageIds.put(orderId, pageId);
        gateway.pay(pageId);
        return orderId;
    }

    /**
     * 与信済みにして、売上を確定する。
     */
    private UUID captured() {
        var orderId = authorized();
        paymentApi.capture(orderId);
        return orderId;
    }

    /**
     * 与信済みにした注文の、支払画面 ID を返す（支払の後は画面の URL を引けないため、覚えておく）。
     */
    private String pageIdOf(UUID orderId) {
        return pageIds.get(orderId);
    }

    private PaymentSummary.Status statusOf(UUID orderId) {
        return paymentApi.find(List.of(orderId)).getFirst().status();
    }}
