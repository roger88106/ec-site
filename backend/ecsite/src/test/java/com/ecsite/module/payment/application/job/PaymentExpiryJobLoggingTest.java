package com.ecsite.module.payment.application.job;

import com.ecsite.LogCapture;
import com.ecsite.TestSettings;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.PaymentCommandService;
import com.ecsite.module.payment.application.PaymentGatewayService;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支払の期限切れのジョブの、例外とログの単体テスト
 *
 * <p>支払の参照・更新のサービスと決済サービスは mock にする。与信の反映を待つ支払の検索は、統合テスト（PaymentExpiryJobTest）で確認する。
 */
class PaymentExpiryJobLoggingTest {

    /**
     * 支払画面を開いた、期限を過ぎた支払
     */
    private static final PastDeadlinePaymentView FIRST = new PastDeadlinePaymentView(UUID.randomUUID(),
            new PaymentPage("page-first", "https://payment.example.com/page-first"));

    /**
     * 支払画面を開いた、期限を過ぎたもう 1 つの支払
     */
    private static final PastDeadlinePaymentView SECOND = new PastDeadlinePaymentView(UUID.randomUUID(),
            new PaymentPage("page-second", "https://payment.example.com/page-second"));

    /**
     * 与信の反映を待つ支払
     */
    private static final AwaitingAuthorizationPaymentView AWAITING_FIRST =
            new AwaitingAuthorizationPaymentView(UUID.randomUUID(), "page-awaiting-first");

    /**
     * 与信の反映を待つもう 1 つの支払
     */
    private static final AwaitingAuthorizationPaymentView AWAITING_SECOND =
            new AwaitingAuthorizationPaymentView(UUID.randomUUID(), "page-awaiting-second");

    /**
     * 決済サービスから取得した与信
     */
    private static final Authorization AUTHORIZATION =
            new Authorization("pi_test_awaiting", Instant.parse("2026-11-06T01:00:00Z"));

    private final PaymentCommandService commandService = mock(PaymentCommandService.class);

    private final PaymentQueryService queryService = mock(PaymentQueryService.class);

    private final PaymentGateway gateway = mock(PaymentGateway.class);

    /**
     * ジョブと、ジョブが呼ぶ決済サービスの処理が出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(PaymentExpiryJob.class, PaymentGatewayService.class);

    private final PaymentExpiryJob job = new PaymentExpiryJob(queryService,
            new PaymentGatewayService(commandService, queryService, gateway, bind(Map.of())));

    @BeforeEach
    void setUp() {
        when(queryService.findPastDeadline(anyInt())).thenReturn(List.of(FIRST, SECOND));
        when(gateway.closePage("page-first")).thenReturn(PaymentGateway.CloseResult.CLOSED);
        when(gateway.closePage("page-second")).thenReturn(PaymentGateway.CloseResult.CLOSED);
    }

    @Test
    @DisplayName("AC-310 決済サービスで予期しない例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterUnexpectedGatewayException() {
        when(gateway.closePage("page-first")).thenThrow(new IllegalStateException("予期しない応答"));

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-310 支払の保存で例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterSaveException() {
        doThrow(new CannotAcquireLockException("ロックを取れない")).when(commandService).expire(FIRST.id());

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-310 決済サービスが応えないときも、今までどおり warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterSystemException() {
        when(gateway.closePage("page-first")).thenThrow(SystemException.of("決済サービスが応えない", null));

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-311 決済サービスで支払済みの支払は、与信の反映を待つ支払にする")
    void awaitsAuthorizationWhenAlreadyPaid() {
        when(gateway.closePage("page-first")).thenReturn(PaymentGateway.CloseResult.ALREADY_PAID);

        job.expire();

        verify(commandService).awaitAuthorization(FIRST.id());
        verify(commandService).expire(SECOND.id());
    }

    @Test
    @DisplayName("AC-312 与信の反映を長く待つ未払いの支払があれば、warn のログに件数を出す")
    void warnsLongAwaitingCount() {
        when(queryService.countLongAwaitingAuthorization()).thenReturn(3L);

        job.expire();

        assertThat(logs.messages(Level.WARN).stream()
                .filter(line -> line.contains("与信の反映を長く待つ支払") && line.contains("count=3")))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-312 与信の反映を長く待つ支払がなければ、ログに出さない")
    void doesNotWarnWithoutLongAwaiting() {
        when(queryService.countLongAwaitingAuthorization()).thenReturn(0L);

        job.expire();

        assertThat(logs.text()).doesNotContain("与信の反映を長く待つ支払");
    }

    @Test
    @DisplayName("AC-409-6 与信を取得できない支払は、warn のログに支払 ID を出して待つ支払のまま残し、残りの支払を処理する")
    void continuesAfterFetchFailure() {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST, AWAITING_SECOND));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenThrow(SystemException.of("予期しない応答", null));
        when(gateway.fetchAuthorization("page-awaiting-second")).thenReturn(AUTHORIZATION);

        job.expire();

        verify(commandService, never()).authorizeFromGateway(eq(AWAITING_FIRST.id()), any());
        verify(commandService).authorizeFromGateway(AWAITING_SECOND.id(), AUTHORIZATION);
        assertThat(warnLinesOf(AWAITING_FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-409-6 支払画面のない行があっても、warn のログに支払 ID を出して、残りの支払を処理する")
    void continuesAfterRowWithoutPage() {
        var broken = new AwaitingAuthorizationPaymentView(UUID.randomUUID(), null);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(broken, AWAITING_SECOND));
        when(gateway.fetchAuthorization("page-awaiting-second")).thenReturn(AUTHORIZATION);

        job.expire();

        verify(commandService, never()).authorizeFromGateway(eq(broken.id()), any());
        verify(commandService).authorizeFromGateway(AWAITING_SECOND.id(), AUTHORIZATION);
        assertThat(warnLinesOf(broken.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-409-9 ジョブの取得で与信済みにしたら、warn のログに支払 ID を出す")
    void warnsWhenAuthorizedFromGateway() {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
        when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(true);

        job.expire();

        assertThat(warnLinesOf(AWAITING_FIRST.id())).singleElement()
                .satisfies(line -> assertThat(line).contains("通知より先に、決済サービスから与信を取得して反映した"));
    }

    @Test
    @DisplayName("AC-409-9 先に通知が届いていて与信済みにしなかったら、warn のログに出さない")
    void doesNotWarnWhenAlreadyAuthorized() {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
        when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(false);

        job.expire();

        assertThat(warnLinesOf(AWAITING_FIRST.id())).isEmpty();
    }

    @Test
    @DisplayName("AC-409-10 決済サービスへの接続の失敗が 3 件続いたら、4 件目以降を取得せず、打ち切ったことを warn のログに出す")
    void stopsAfterConsecutiveUnavailable() {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        payments.forEach(payment -> when(gateway.fetchAuthorization(payment.pageId())).thenThrow(unavailable()));

        job.expire();

        verify(gateway, times(3)).fetchAuthorization(anyString());
        verify(gateway, never()).fetchAuthorization(payments.get(3).pageId());
        assertThat(logs.messages(Level.WARN).stream()
                .filter(line -> line.contains("この回の与信の取得を打ち切る") && line.contains("count=3")))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-409-10 接続の失敗の間に成功が入ったら、連続した失敗の件数を 0 に戻して続ける")
    void resetsCountOnSuccess() {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        when(gateway.fetchAuthorization(payments.getFirst().pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(2).pageId())).thenReturn(AUTHORIZATION);
        when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

        job.expire();

        verify(gateway, times(5)).fetchAuthorization(anyString());
    }

    @Test
    @DisplayName("AC-409-10 接続の失敗の間に他の失敗が入ったら、連続した失敗の件数を 0 に戻して続ける")
    void resetsCountOnOtherFailure() {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        when(gateway.fetchAuthorization(payments.getFirst().pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(2).pageId())).thenThrow(SystemException.of("予期しない応答", null));
        when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

        job.expire();

        verify(gateway, times(5)).fetchAuthorization(anyString());
    }

    @Test
    @DisplayName("AC-312 与信の反映を長く待つと見なす時間は、既定で 1 時間。設定で変えられる")
    void awaitingAuthorizationWarningSetting() {
        assertThat(bind(Map.of()).awaitingAuthorizationWarning()).isEqualTo(Duration.ofHours(1));
        assertThat(bind(Map.of("awaiting-authorization-warning", "30m")).awaitingAuthorizationWarning())
                .isEqualTo(Duration.ofMinutes(30));
    }

    // ============================== 補助 ==============================

    /**
     * 与信の反映を待つ支払を、指定の数だけ作る。
     */
    private static List<AwaitingAuthorizationPaymentView> awaitingPayments(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new AwaitingAuthorizationPaymentView(UUID.randomUUID(), "page-awaiting-" + index))
                .toList();
    }

    /**
     * 決済サービスへの接続の失敗
     */
    private static SystemException unavailable() {
        return SystemException.of("接続の失敗", null, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    /**
     * 支払 ID を含む warn のログの文
     */
    private List<String> warnLinesOf(UUID paymentId) {
        return logs.messages(Level.WARN).stream()
                .filter(line -> line.contains(paymentId.toString()))
                .toList();
    }

    /**
     * 決済の設定に値を当てはめる（戻る先の URL は必須なので、いつも入れる）。
     */
    private static PaymentProperties bind(Map<String, String> values) {
        var source = new HashMap<String, String>();
        source.put("return-url", "http://localhost:3000/orders/{orderId}");
        source.putAll(values);
        return TestSettings.bind("ecsite.payment", PaymentProperties.class, source);
    }
}
