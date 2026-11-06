package com.ecsite.module.payment.application;

import com.ecsite.TestSettings;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 支払の期限切れのジョブの、例外とログの単体テスト。<br />
 * 支払の参照・更新のサービスと決済サービスは mock にする。与信の反映を待つ支払の検索は、統合テスト（PaymentExpiryJobTest）で確認する。
 */
@ExtendWith(OutputCaptureExtension.class)
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
    private static final PaymentGateway.Authorization AUTHORIZATION =
            new PaymentGateway.Authorization("pi_test_awaiting", Instant.parse("2026-11-06T01:00:00Z"));

    private final PaymentCommandService commandService = mock(PaymentCommandService.class);

    private final PaymentQueryService queryService = mock(PaymentQueryService.class);

    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private final PaymentExpiryJob job = new PaymentExpiryJob(commandService, queryService, gateway);

    @BeforeEach
    void setUp() {
        when(queryService.findPastDeadline(anyInt())).thenReturn(List.of(FIRST, SECOND));
        when(gateway.closePage("page-first")).thenReturn(PaymentGateway.CloseResult.CLOSED);
        when(gateway.closePage("page-second")).thenReturn(PaymentGateway.CloseResult.CLOSED);
    }

    @Test
    @DisplayName("AC-310 決済サービスで予期しない例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterUnexpectedGatewayException(CapturedOutput output) {
        when(gateway.closePage("page-first")).thenThrow(new IllegalStateException("予期しない応答"));

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(output, FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-310 支払の保存で例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterSaveException(CapturedOutput output) {
        doThrow(new CannotAcquireLockException("ロックを取れない")).when(commandService).expire(FIRST.id());

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(output, FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-310 決済サービスが応えないときも、今までどおり warn のログに支払 ID を出して、残りの支払を期限切れにする")
    void continuesAfterSystemException(CapturedOutput output) {
        when(gateway.closePage("page-first")).thenThrow(SystemException.of("決済サービスが応えない", null));

        job.expire();

        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(output, FIRST.id())).hasSize(1);
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
    void warnsLongAwaitingCount(CapturedOutput output) {
        when(queryService.countLongAwaitingAuthorization()).thenReturn(3L);

        job.expire();

        assertThat(output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains("与信の反映を長く待つ支払") && line.contains("count=3")))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-312 与信の反映を長く待つ支払がなければ、ログに出さない")
    void doesNotWarnWithoutLongAwaiting(CapturedOutput output) {
        when(queryService.countLongAwaitingAuthorization()).thenReturn(0L);

        job.expire();

        assertThat(output.getAll()).doesNotContain("与信の反映を長く待つ支払");
    }

    @Test
    @DisplayName("AC-409-6 与信を取得できない支払は、warn のログに支払 ID を出して待つ支払のまま残し、残りの支払を処理する")
    void continuesAfterFetchFailure(CapturedOutput output) {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST, AWAITING_SECOND));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenThrow(SystemException.of("予期しない応答", null));
        when(gateway.fetchAuthorization("page-awaiting-second")).thenReturn(AUTHORIZATION);

        job.expire();

        verify(commandService, never()).authorizeFromGateway(eq(AWAITING_FIRST.id()), any());
        verify(commandService).authorizeFromGateway(AWAITING_SECOND.id(), AUTHORIZATION);
        assertThat(warnLinesOf(output, AWAITING_FIRST.id())).hasSize(1);
    }

    @Test
    @DisplayName("AC-409-9 ジョブの取得で与信済みにしたら、warn のログに支払 ID を出す")
    void warnsWhenAuthorizedFromGateway(CapturedOutput output) {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
        when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(true);

        job.expire();

        assertThat(warnLinesOf(output, AWAITING_FIRST.id())).singleElement()
                .satisfies(line -> assertThat(line).contains("与信の通知が届いていないので"));
    }

    @Test
    @DisplayName("AC-409-9 先に通知が届いていて与信済みにしなかったら、warn のログに出さない")
    void doesNotWarnWhenAlreadyAuthorized(CapturedOutput output) {
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
        when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
        when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(false);

        job.expire();

        assertThat(warnLinesOf(output, AWAITING_FIRST.id())).isEmpty();
    }

    @Test
    @DisplayName("AC-409-10 決済サービスに接続できない失敗が 3 件続いたら、4 件目以降を取得せず、打ち切ったことを warn のログに出す")
    void stopsAfterConsecutiveUnavailable(CapturedOutput output) {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        payments.forEach(payment -> when(gateway.fetchAuthorization(payment.pageId())).thenThrow(unavailable()));

        job.expire();

        verify(gateway, times(3)).fetchAuthorization(anyString());
        verify(gateway, never()).fetchAuthorization(payments.get(3).pageId());
        assertThat(output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains("この回の与信の取得を打ち切る") && line.contains("count=3")))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-409-10 接続できない失敗の間に成功が入ったら、数を 0 に戻して続ける")
    void resetsCountOnSuccess() {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        when(gateway.fetchAuthorization(payments.get(0).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(2).pageId())).thenReturn(AUTHORIZATION);
        when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

        job.expire();

        verify(gateway, times(5)).fetchAuthorization(anyString());
    }

    @Test
    @DisplayName("AC-409-10 接続できない失敗の間に他の失敗が入ったら、数を 0 に戻して続ける")
    void resetsCountOnOtherFailure() {
        var payments = awaitingPayments(5);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        when(gateway.fetchAuthorization(payments.get(0).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(2).pageId())).thenThrow(SystemException.of("予期しない応答", null));
        when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
        when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

        job.expire();

        verify(gateway, times(5)).fetchAuthorization(anyString());
    }

    @Test
    @DisplayName("AC-312 与信の反映を長く待つとする時間は、既定で 1 時間。設定で変えられる")
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
     * 決済サービスに接続できない失敗
     */
    private static SystemException unavailable() {
        return SystemException.of("接続できない", null, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    /**
     * 支払 ID を含む warn のログの行
     */
    private static List<String> warnLinesOf(CapturedOutput output, UUID paymentId) {
        return output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains(paymentId.toString()))
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
