package com.ecsite.module.payment.application.job;

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
import com.ecsite.support.LogCapture;
import com.ecsite.support.TestSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    // ============================== 定数 ==============================

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

    /**
     * 与信の取得を打ち切る件数（決済サービスに続けて接続できなかった数）
     */
    private static final int UNAVAILABLE_LIMIT = 3;

    /**
     * 決済の設定の接頭辞
     */
    private static final String PREFIX = "ecsite.payment";

    // ============================== 対象 ==============================

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService = mock(PaymentCommandService.class);

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService = mock(PaymentQueryService.class);

    /**
     * 決済サービス
     */
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    /**
     * ジョブと、ジョブが呼ぶ決済サービスの処理が出したログ
     */
    @RegisterExtension
    final LogCapture logs = LogCapture.of(PaymentExpiryJob.class, PaymentGatewayService.class);

    /**
     * 確認するジョブ
     */
    private final PaymentExpiryJob job = new PaymentExpiryJob(queryService,
            new PaymentGatewayService(commandService, queryService, gateway, bind(Map.of())));

    @BeforeEach
    void setUp() {
        when(queryService.findPastDeadline(anyInt())).thenReturn(List.of(FIRST, SECOND));
        when(gateway.closePage("page-first")).thenReturn(PaymentGateway.CloseResult.CLOSED);
        when(gateway.closePage("page-second")).thenReturn(PaymentGateway.CloseResult.CLOSED);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("期限を過ぎた支払の例外")
    class ExpiryFailure {

        @Test
        @DisplayName("AC-310 決済サービスで予期しない例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
        void continuesAfterUnexpectedGatewayException() {
            // 準備：1 件目の画面を閉じると、予期しない例外
            when(gateway.closePage("page-first")).thenThrow(new IllegalStateException("予期しない応答"));

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目を飛ばして続けた
            assertSkippedAndContinued();
        }

        @Test
        @DisplayName("AC-310 支払の保存で例外が起きても、warn のログに支払 ID を出して、残りの支払を期限切れにする")
        void continuesAfterSaveException() {
            // 準備：1 件目の保存で、ロックを取れない
            doThrow(new CannotAcquireLockException("ロックを取れない")).when(commandService).expire(FIRST.id());

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目を飛ばして続けた
            assertSkippedAndContinued();
        }

        @Test
        @DisplayName("AC-310 決済サービスが応えないときも、warn のログに支払 ID を出して、残りの支払を期限切れにする")
        void continuesAfterSystemException() {
            // 準備：1 件目の画面を閉じると、システムのエラー
            when(gateway.closePage("page-first")).thenThrow(SystemException.of("決済サービスが応えない", null));

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目を飛ばして続けた
            assertSkippedAndContinued();
        }

        @Test
        @DisplayName("AC-311 決済サービスで支払済みの支払は、与信の反映を待つ支払にし、残りの支払を期限切れにする")
        void awaitsAuthorizationWhenAlreadyPaid() {
            // 準備：1 件目は決済サービスで支払済み
            when(gateway.closePage("page-first")).thenReturn(PaymentGateway.CloseResult.ALREADY_PAID);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目は待つ支払に、2 件目は期限切れに
            verify(commandService).awaitAuthorization(FIRST.id());
            verify(commandService).expire(SECOND.id());
        }
    }

    @Nested
    @DisplayName("与信の反映を長く待つ支払の警告")
    class LongAwaitingWarning {

        @Test
        @DisplayName("AC-312 与信の反映を長く待つ未払いの支払があれば、warn のログに件数を出す")
        void warnsLongAwaitingCount() {
            // 準備：長く待つ支払が 3 件
            when(queryService.countLongAwaitingAuthorization()).thenReturn(3L);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：件数の warn が 1 行
            assertThat(logs.messages(Level.WARN).stream()
                    .filter(line -> line.contains("与信の反映を長く待つ支払") && line.contains("count=3")))
                    .hasSize(1);
        }

        @Test
        @DisplayName("AC-312 与信の反映を長く待つ支払がなければ、ログに出さない")
        void doesNotWarnWithoutLongAwaiting() {
            // 準備：長く待つ支払がない
            when(queryService.countLongAwaitingAuthorization()).thenReturn(0L);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：ログに出さない
            assertThat(logs.text()).doesNotContain("与信の反映を長く待つ支払");
        }

        @Test
        @DisplayName("AC-312 与信の反映を長く待つと見なす時間は、既定で 1 時間")
        void awaitingAuthorizationWarningDefault() {
            // 操作：起動したアプリが使う設定ファイルの値を読む（戻る先の URL は local にだけある）
            var properties = TestSettings.loadAndBind(PREFIX, PaymentProperties.class, "local");

            // 確認：1 時間
            assertThat(properties.awaitingAuthorizationWarning()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("AC-312 与信の反映を長く待つと見なす時間は、設定で変えられる")
        void awaitingAuthorizationWarningOverride() {
            // 操作：30 分を設定する
            var properties = bind(Map.of("awaiting-authorization-warning", "30m"));

            // 確認：30 分
            assertThat(properties.awaitingAuthorizationWarning()).isEqualTo(Duration.ofMinutes(30));
        }
    }

    @Nested
    @DisplayName("与信の取得の失敗")
    class FetchFailure {

        @Test
        @DisplayName("AC-409-6 与信を取得できない支払は、warn のログに支払 ID を出して待つ支払のまま残し、残りの支払を処理する")
        void continuesAfterFetchFailure() {
            // 準備：1 件目は与信を取得できず、2 件目は取得できる
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST, AWAITING_SECOND));
            when(gateway.fetchAuthorization("page-awaiting-first")).thenThrow(SystemException.of("予期しない応答", null));
            when(gateway.fetchAuthorization("page-awaiting-second")).thenReturn(AUTHORIZATION);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目は待つまま残し、2 件目を与信済みにした
            assertFetchSkippedAndContinued(AWAITING_FIRST.id());
        }

        @Test
        @DisplayName("AC-409-6 支払画面のない行があっても、warn のログに支払 ID を出して、残りの支払を処理する")
        void continuesAfterRowWithoutPage() {
            // 準備：1 件目は支払画面のない行
            var broken = new AwaitingAuthorizationPaymentView(UUID.randomUUID(), null);
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(broken, AWAITING_SECOND));
            when(gateway.fetchAuthorization("page-awaiting-second")).thenReturn(AUTHORIZATION);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：1 件目は与信済みにせず、2 件目を与信済みにした
            assertFetchSkippedAndContinued(broken.id());
        }

        @Test
        @DisplayName("AC-409-10 決済サービスへの接続の失敗が 3 件続いたら、その回の 4 件目以降の与信を取得しない")
        void stopsAfterConsecutiveUnavailable() {
            // 準備：5 件の全てで接続に失敗する
            var payments = awaitingPaymentsAllUnavailable(5);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：3 件だけ取得し、4 件目は取得しない
            verify(gateway, times(UNAVAILABLE_LIMIT)).fetchAuthorization(anyString());
            verify(gateway, never()).fetchAuthorization(payments.get(3).pageId());
        }

        @Test
        @DisplayName("AC-409-10 与信の取得を打ち切ったら、その旨を warn のログに出す")
        void warnsWhenStopped() {
            // 準備：5 件の全てで接続に失敗する
            awaitingPaymentsAllUnavailable(5);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：打ち切りの warn が 1 行
            assertThat(logs.messages(Level.WARN).stream()
                    .filter(line -> line.contains("この回の与信の取得を打ち切る") && line.contains("count=3")))
                    .hasSize(1);
        }

        @Test
        @DisplayName("AC-409-10 接続の失敗の間に成功が入ったら、連続した失敗の件数を 0 に戻して続ける")
        void resetsCountOnSuccess() {
            // 準備：失敗 2 件、成功 1 件、失敗 2 件
            var payments = awaitingPayments(5);
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
            when(gateway.fetchAuthorization(payments.getFirst().pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(2).pageId())).thenReturn(AUTHORIZATION);
            when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

            // 操作：ジョブを動かす
            job.expire();

            // 確認：5 件とも取得した
            verify(gateway, times(5)).fetchAuthorization(anyString());
        }

        @Test
        @DisplayName("AC-409-10 接続の失敗の間に他の失敗が入ったら、連続した失敗の件数を 0 に戻して続ける")
        void resetsCountOnOtherFailure() {
            // 準備：接続の失敗 2 件、予期しない失敗 1 件、接続の失敗 2 件
            var payments = awaitingPayments(5);
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
            when(gateway.fetchAuthorization(payments.getFirst().pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(1).pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(2).pageId())).thenThrow(SystemException.of("予期しない応答", null));
            when(gateway.fetchAuthorization(payments.get(3).pageId())).thenThrow(unavailable());
            when(gateway.fetchAuthorization(payments.get(4).pageId())).thenThrow(unavailable());

            // 操作：ジョブを動かす
            job.expire();

            // 確認：5 件とも取得した
            verify(gateway, times(5)).fetchAuthorization(anyString());
        }
    }

    @Nested
    @DisplayName("ジョブの取得で与信済みにしたときのログ")
    class AuthorizedFromGatewayLog {

        @Test
        @DisplayName("AC-409-9 ジョブの取得で与信済みにしたら、warn のログに支払 ID を出す")
        void warnsWhenAuthorizedFromGateway() {
            // 準備：取得した与信で与信済みになる
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
            when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
            when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(true);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：支払 ID を含む warn が 1 行で、取得して反映したと書いてある
            assertThat(warnLinesOf(AWAITING_FIRST.id())).singleElement()
                    .satisfies(line -> assertThat(line).contains("通知より先に、決済サービスから与信を取得して反映した"));
        }

        @Test
        @DisplayName("AC-409-9 先に通知が届いていて与信済みにしなかったら、warn のログに出さない")
        void doesNotWarnWhenAlreadyAuthorized() {
            // 準備：先に通知が届いていて、与信済みにならない
            when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(List.of(AWAITING_FIRST));
            when(gateway.fetchAuthorization("page-awaiting-first")).thenReturn(AUTHORIZATION);
            when(commandService.authorizeFromGateway(AWAITING_FIRST.id(), AUTHORIZATION)).thenReturn(false);

            // 操作：ジョブを動かす
            job.expire();

            // 確認：支払 ID を含む warn がない
            assertThat(warnLinesOf(AWAITING_FIRST.id())).isEmpty();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 期限を過ぎた 1 件目（{@link #FIRST}）で例外が起きても、飛ばして続けたことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>2 件目（{@link #SECOND}）を期限切れにした</li>
     *   <li>1 件目の支払 ID を含む warn のログが 1 行</li>
     * </ul>
     */
    private void assertSkippedAndContinued() {
        verify(commandService).expire(SECOND.id());
        assertThat(warnLinesOf(FIRST.id())).hasSize(1);
    }

    /**
     * 与信の反映を待つ 1 件目を与信済みにせず、2 件目（{@link #AWAITING_SECOND}）を与信済みにしたことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>1 件目を与信済みにしない（待つ支払のまま）</li>
     *   <li>2 件目を、取得した与信で与信済みにした</li>
     *   <li>1 件目の支払 ID を含む warn のログが 1 行</li>
     * </ul>
     *
     * @param skippedId 飛ばした 1 件目の支払 ID
     */
    private void assertFetchSkippedAndContinued(UUID skippedId) {
        verify(commandService, never()).authorizeFromGateway(eq(skippedId), any());
        verify(commandService).authorizeFromGateway(AWAITING_SECOND.id(), AUTHORIZATION);
        assertThat(warnLinesOf(skippedId)).hasSize(1);
    }

    /**
     * 支払 ID を含む warn のログの文を返す。
     *
     * @param paymentId 支払 ID
     * @return 文の一覧
     */
    private List<String> warnLinesOf(UUID paymentId) {
        return logs.messages(Level.WARN).stream()
                .filter(line -> line.contains(paymentId.toString()))
                .toList();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 与信の反映を待つ支払を、指定の数だけ作る。
     *
     * @param count 数
     * @return 支払
     */
    private static List<AwaitingAuthorizationPaymentView> awaitingPayments(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new AwaitingAuthorizationPaymentView(UUID.randomUUID(), "page-awaiting-" + index))
                .toList();
    }

    /**
     * 与信の反映を待つ支払を指定の数だけ用意し、全ての与信の取得を接続の失敗にする。
     *
     * @param count 数
     * @return 支払
     */
    private List<AwaitingAuthorizationPaymentView> awaitingPaymentsAllUnavailable(int count) {
        var payments = awaitingPayments(count);
        when(queryService.findAwaitingAuthorization(anyInt())).thenReturn(payments);
        payments.forEach(payment -> when(gateway.fetchAuthorization(payment.pageId())).thenThrow(unavailable()));
        return payments;
    }

    /**
     * 決済サービスへの接続の失敗を作る。
     *
     * @return 例外
     */
    private static SystemException unavailable() {
        return SystemException.of("接続の失敗", null, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    /**
     * 決済の設定に値を当てはめる（戻る先の URL は必須なので、いつも入れる）。
     *
     * @param values 接頭辞の下の名前と値
     * @return 決済の設定
     */
    private static PaymentProperties bind(Map<String, String> values) {
        var source = new HashMap<String, String>();
        source.put("return-url", "http://localhost:3000/orders/{orderId}");
        source.putAll(values);
        return TestSettings.bind(PREFIX, PaymentProperties.class, source);
    }
}
