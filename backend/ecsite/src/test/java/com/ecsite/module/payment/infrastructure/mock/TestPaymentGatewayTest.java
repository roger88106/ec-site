package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureState;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.VoidResult;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentId;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway.Operation;
import com.ecsite.support.MutableClock;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * テスト用の決済サービス（{@link TestPaymentGateway}）の振る舞いの検証
 *
 * <p>公開の操作だけで確認する。包んだ mock に届いたかは、mock の読み取りの操作と Mockito の spy で確認する。
 */
@DisplayName("テスト用の決済サービス")
class TestPaymentGatewayTest {

    // ============================== 定数 ==============================

    /**
     * 支払を済ませた時刻
     */
    private static final Instant PAID_AT = Instant.parse("2026-11-06T01:00:00Z");

    /**
     * 支払画面から戻る店舗の注文の画面
     */
    private static final String RETURN_URL = "http://localhost:3000/orders";

    /**
     * 渡す金額
     */
    private static final Money AMOUNT = Money.of(6940);

    /**
     * 2 つ目の金額
     */
    private static final Money OTHER_AMOUNT = Money.of(1200);

    /**
     * 与信の参照
     */
    private static final String REF_A = "mock_auth_a";

    /**
     * 別の与信の参照
     */
    private static final String REF_B = "mock_auth_b";

    /**
     * 存在しない支払画面 ID
     */
    private static final String UNKNOWN_PAGE_ID = "mock_page_unknown";

    // ============================== 対象 ==============================

    /**
     * mock が支払を済ませた時刻を読む時計
     */
    private final MutableClock clock = new MutableClock(PAID_AT);

    /**
     * 通知の port
     */
    private final PaymentNotificationReceiver receiver = mock(PaymentNotificationReceiver.class);

    /**
     * 包まれる本番の mock（呼ばれたかを確認するため spy にする）
     */
    private final MockPaymentGateway inner = spy(new MockPaymentGateway(
            receiver, new MockPaymentProperties("http://localhost:8080"), clock, UUID::randomUUID));

    /**
     * 確認する対象
     */
    private final TestPaymentGateway gateway = new TestPaymentGateway(inner);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("支払画面の作成")
    class OpenPage {

        @Test
        @DisplayName("作った画面の ID を作った順に記録し、数も合う")
        void recordsOpenedPageIdsInOrder() {
            // 操作：2 つの画面を作る
            var first = openPage(newOrderId());
            var second = openPage(newOrderId());

            // 確認：作った順の ID と数
            assertThat(gateway.openedPageIds()).containsExactly(first, second);
            assertThat(gateway.openedPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("渡した金額を注文ごとに記録する")
        void recordsAmount() {
            // 準備：注文
            var orderId = newOrderId();

            // 操作：画面を作る
            openPage(orderId);

            // 確認：金額が記録される
            assertThat(gateway.amountsOf(Operation.OPEN_PAGE, orderId.value())).containsExactly(AMOUNT);
        }

        @Test
        @DisplayName("失敗を指定すると外部サービスのエラーになり、金額は記録されるが画面は作られない")
        void failsWithoutOpeningPage() {
            // 準備：1 回失敗させる
            var orderId = newOrderId();
            gateway.failNextOpens(1);

            // 操作と確認：外部サービスのエラーになる
            assertUnavailable(() -> gateway.openPage(pageRequest(orderId)));

            // 確認：金額は記録され、画面は作られず、mock にも届かない
            assertThat(gateway.amountsOf(Operation.OPEN_PAGE, orderId.value())).containsExactly(AMOUNT);
            assertThat(gateway.openedPageIds()).isEmpty();
            assertThat(gateway.openedPages()).isZero();
            verify(inner, never()).openPage(any());
        }

        @Test
        @DisplayName("指定した回数の失敗の次は成功する")
        void succeedsAfterFailures() {
            // 準備：2 回失敗させる
            gateway.failNextOpens(2);
            var orderId = newOrderId();

            // 操作：3 回呼ぶ
            assertUnavailable(() -> gateway.openPage(pageRequest(orderId)));
            assertUnavailable(() -> gateway.openPage(pageRequest(orderId)));
            var pageId = openPage(orderId);

            // 確認：3 回目で画面ができる
            assertThat(gateway.openedPageIds()).containsExactly(pageId);
            assertThat(gateway.amountsOf(Operation.OPEN_PAGE, orderId.value())).hasSize(3);
        }

        @Test
        @DisplayName("差し込んだ処理は、画面を作った後に 1 回だけ動く")
        void runsDuringNextOpenOnceAfterOpening() {
            // 準備：処理の中で、作った画面の ID の記録を読む
            var runs = new AtomicInteger();
            var seen = new ArrayList<List<String>>();
            gateway.duringNextOpen(() -> {
                runs.incrementAndGet();
                seen.add(gateway.openedPageIds());
            });

            // 操作：2 つの画面を作る
            var first = openPage(newOrderId());
            openPage(newOrderId());

            // 確認：1 回だけ動き、動いたときは 1 つ目の画面が記録されている
            assertThat(runs).hasValue(1);
            assertThat(seen).containsExactly(List.of(first));
        }

        @Test
        @DisplayName("差し込んだ処理は、失敗した回では動かず、次の成功した回で動く")
        void runsDuringNextOpenOnlyOnSuccess() {
            // 準備：差し込んだ処理と、1 回の失敗
            var runs = new AtomicInteger();
            gateway.duringNextOpen(runs::incrementAndGet);
            gateway.failNextOpens(1);
            var orderId = newOrderId();

            // 操作：失敗する回を呼ぶ
            assertUnavailable(() -> gateway.openPage(pageRequest(orderId)));

            // 確認：動かない
            assertThat(runs).hasValue(0);

            // 操作：成功する回を呼ぶ
            openPage(orderId);

            // 確認：動く
            assertThat(runs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("画面を閉じる")
    class ClosePage {

        @Test
        @DisplayName("呼ばれた回数を、画面ごとに、重複も含めて数える")
        void countsCallsPerPage() {
            // 準備：2 つの画面
            var pageA = openPage(newOrderId());
            var pageB = openPage(newOrderId());

            // 操作：A を 2 回、B を 1 回閉じる
            gateway.closePage(pageA);
            gateway.closePage(pageA);
            gateway.closePage(pageB);

            // 確認：画面ごとの回数
            assertThat(gateway.closeCallsOf(pageA)).isEqualTo(2);
            assertThat(gateway.closeCallsOf(pageB)).isEqualTo(1);
        }

        @Test
        @DisplayName("失敗を指定すると外部サービスのエラーになり、回数に含まれ、画面は開いたままになる")
        void failsAndKeepsPageOpen() {
            // 準備：画面と、1 回の失敗
            var pageId = openPage(newOrderId());
            gateway.failNextCloses(1);

            // 操作と確認：外部サービスのエラーになる
            assertUnavailable(() -> gateway.closePage(pageId));

            // 確認：回数に含まれ、画面は開いたまま
            assertThat(gateway.closeCallsOf(pageId)).isEqualTo(1);
            assertThat(gateway.page(pageId)).get().extracting(MockPaymentGateway.Page::status)
                    .isEqualTo(MockPaymentGateway.PageStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("与信の取得")
    class FetchAuthorization {

        @Test
        @DisplayName("呼ばれた回数に、失敗を含める")
        void countsCallsIncludingFailures() {
            // 準備：支払の済んだ画面と、1 回の失敗
            var pageId = paidPage();
            gateway.failNextAuthorizationFetches(1);

            // 操作：失敗する回と成功する回を呼ぶ
            assertUnavailable(() -> gateway.fetchAuthorization(pageId));
            gateway.fetchAuthorization(pageId);

            // 確認：2 回
            assertThat(gateway.fetchAuthorizationCalls()).isEqualTo(2);
        }

        @Test
        @DisplayName("呼ばれた時点の処理は、呼ばれるたびに、失敗する回でも動く")
        void runsOnFetchAuthorizationEveryCall() {
            // 準備：呼ばれた時点の処理と、1 回の失敗
            var pageId = paidPage();
            var runs = new AtomicInteger();
            gateway.onFetchAuthorization(runs::incrementAndGet);
            gateway.failNextAuthorizationFetches(1);

            // 操作：失敗する回と成功する回を呼ぶ
            assertUnavailable(() -> gateway.fetchAuthorization(pageId));
            gateway.fetchAuthorization(pageId);

            // 確認：2 回動く
            assertThat(runs).hasValue(2);
        }

        @Test
        @DisplayName("指定した回数の失敗は外部サービスのエラーで、その次は与信を取得できる")
        void failsWithUnavailableThenSucceeds() {
            // 準備：支払の済んだ画面と、2 回の失敗
            var pageId = paidPage();
            gateway.failNextAuthorizationFetches(2);

            // 操作と確認：2 回は外部サービスのエラー
            assertUnavailable(() -> gateway.fetchAuthorization(pageId));
            assertUnavailable(() -> gateway.fetchAuthorization(pageId));

            // 操作と確認：3 回目は取得できる
            assertThat(gateway.fetchAuthorization(pageId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("拒否を指定した画面だけ、予期しないエラーで失敗し続ける")
        void rejectsOnlySpecifiedPage() {
            // 準備：拒否する画面と、しない画面
            var rejected = paidPage();
            var other = paidPage();
            gateway.rejectAuthorizationFetch(rejected);

            // 操作と確認：拒否した画面は繰り返し失敗し、他の画面は取得できる
            assertSystemFailure(() -> gateway.fetchAuthorization(rejected));
            assertSystemFailure(() -> gateway.fetchAuthorization(rejected));
            assertThat(gateway.fetchAuthorization(other).authorizationRef()).isEqualTo(gateway.authorizationRefOf(other));
        }

        @Test
        @DisplayName("回数の失敗と拒否の両方を指定すると、回数の失敗が先になる")
        void failsByCountBeforeRejection() {
            // 準備：1 回の失敗と、同じ画面の拒否
            var pageId = paidPage();
            gateway.failNextAuthorizationFetches(1);
            gateway.rejectAuthorizationFetch(pageId);

            // 操作と確認：先に外部サービスのエラー、次に予期しないエラー
            assertUnavailable(() -> gateway.fetchAuthorization(pageId));
            assertSystemFailure(() -> gateway.fetchAuthorization(pageId));
        }
    }

    @Nested
    @DisplayName("与信の取消")
    class VoidAuthorization {

        @Test
        @DisplayName("取り消した与信を順に記録し、同じ与信を 2 回取り消すと 2 回記録する")
        void recordsVoidedInOrder() {
            // 操作：A、B、A の順に取り消す
            gateway.voidAuthorization(REF_A);
            gateway.voidAuthorization(REF_B);
            gateway.voidAuthorization(REF_A);

            // 確認：取り消した順に記録され、A は 2 回
            assertThat(gateway.voided()).containsExactly(REF_A, REF_B, REF_A);
            assertThat(gateway.voidsOf(REF_A)).isEqualTo(2);
        }

        @Test
        @DisplayName("売上を確定した与信の取消は ALREADY_CAPTURED になり、取り消した記録に入らない")
        void rejectsCapturedAuthorization() {
            // 準備：売上を確定した与信
            gateway.capture(captureRequest(newOrderId(), REF_A));

            // 操作：取り消す
            var result = gateway.voidAuthorization(REF_A);

            // 確認：取り消せず、記録に入らない
            assertThat(result).isEqualTo(VoidResult.ALREADY_CAPTURED);
            assertThat(gateway.voided()).isEmpty();
        }

        @Test
        @DisplayName("失敗を指定すると予期しないエラーになり、取り消した記録に入らない")
        void failsWithoutRecording() {
            // 準備：1 回の失敗
            gateway.failNextVoids(1);

            // 操作と確認：予期しないエラー
            assertSystemFailure(() -> gateway.voidAuthorization(REF_A));

            // 確認：記録に入らない
            assertThat(gateway.voided()).isEmpty();
            assertThat(gateway.voidsOf(REF_A)).isZero();
        }

        @Test
        @DisplayName("差し込んだ処理は、成功する回でも、取消の記録より先に動く")
        void runsOnRefundOrVoidBeforeRecording() {
            // 準備：処理の中で、取り消した与信の記録を読む
            var seen = new ArrayList<List<String>>();
            gateway.onRefundOrVoid(() -> seen.add(gateway.voided()));

            // 操作：取り消す
            gateway.voidAuthorization(REF_A);

            // 確認：処理が動いたとき、記録は空だった。取消は記録された
            assertThat(seen).containsExactly(List.of());
            assertThat(gateway.voided()).containsExactly(REF_A);
        }

        @Test
        @DisplayName("差し込んだ処理は、失敗する回でも先に動く")
        void runsOnRefundOrVoidBeforeFailure() {
            // 準備：呼ばれた時点の処理と、1 回の失敗
            var runs = new AtomicInteger();
            gateway.onRefundOrVoid(runs::incrementAndGet);
            gateway.failNextVoids(1);

            // 操作と確認：失敗する
            assertSystemFailure(() -> gateway.voidAuthorization(REF_A));

            // 確認：処理は動いた
            assertThat(runs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("呼ばれた回数を、全体と注文ごとに記録する")
        void recordsCallCounts() {
            // 準備：2 つの注文
            var orderA = newOrderId();
            var orderB = newOrderId();

            // 操作：A を 2 回、B を 1 回呼ぶ
            gateway.capture(captureRequest(orderA, REF_A));
            gateway.capture(captureRequest(orderA, REF_A));
            gateway.capture(captureRequest(orderB, REF_B));

            // 確認：全体と注文ごとの回数
            assertThat(gateway.captureCalls()).isEqualTo(3);
            assertThat(gateway.captureCallsOf(orderA.value())).isEqualTo(2);
            assertThat(gateway.captureCallsOf(orderB.value())).isEqualTo(1);
        }

        @Test
        @DisplayName("渡した金額を呼ばれた順に記録する")
        void recordsAmounts() {
            // 準備：注文
            var orderId = newOrderId();

            // 操作：違う金額で 2 回呼ぶ
            gateway.capture(new CaptureRequest(orderId, REF_A, AMOUNT));
            gateway.capture(new CaptureRequest(orderId, REF_A, OTHER_AMOUNT));

            // 確認：呼ばれた順の金額
            assertThat(gateway.amountsOf(Operation.CAPTURE, orderId.value())).containsExactly(AMOUNT, OTHER_AMOUNT);
        }

        @Test
        @DisplayName("与信の参照は最後に渡したものを記録し、呼ばれていない注文は空になる")
        void recordsLastAuthorizationRef() {
            // 準備：注文
            var orderId = newOrderId();

            // 操作：違う与信で 2 回呼ぶ
            gateway.capture(captureRequest(orderId, REF_A));
            gateway.capture(captureRequest(orderId, REF_B));

            // 確認：最後のもの。呼ばれていない注文は空
            assertThat(gateway.capturedAuthorizationRef(orderId.value())).contains(REF_B);
            assertThat(gateway.capturedAuthorizationRef(newOrderId().value())).isEmpty();
        }

        @Test
        @DisplayName("確定した注文 ID を確定した順に記録する")
        void recordsCapturedOrderIdsInOrder() {
            // 準備：2 つの注文
            var orderA = newOrderId();
            var orderB = newOrderId();

            // 操作：B、A の順に確定する
            gateway.capture(captureRequest(orderB, REF_B));
            gateway.capture(captureRequest(orderA, REF_A));

            // 確認：確定した順
            assertThat(gateway.captured()).containsExactly(orderB.value(), orderA.value());
        }

        @Test
        @DisplayName("同じ注文を 2 回確定すると、呼ばれた回数は 2 回、確定した注文 ID は 1 回になる")
        void recordsDuplicateCaptureOnce() {
            // 準備：注文
            var orderId = newOrderId();

            // 操作：2 回確定する
            gateway.capture(captureRequest(orderId, REF_A));
            gateway.capture(captureRequest(orderId, REF_A));

            // 確認：呼ばれた回数は 2、注文 ID は 1 回
            assertThat(gateway.captureCalls()).isEqualTo(2);
            assertThat(gateway.captured()).containsExactly(orderId.value());
        }

        @Test
        @DisplayName("接続できない指定をすると UNREACHABLE になり、回数と金額は記録するが、確定しない")
        void unreachableDoesNotCapture() {
            // 準備：1 回接続できなくする
            var orderId = newOrderId();
            gateway.failNextCaptures(1);

            // 操作：確定する
            var result = gateway.capture(captureRequest(orderId, REF_A));

            // 確認：結果と記録。mock では確定していない
            assertThat(result).isEqualTo(CaptureResult.UNREACHABLE);
            assertThat(gateway.captureCalls()).isEqualTo(1);
            assertThat(gateway.amountsOf(Operation.CAPTURE, orderId.value())).containsExactly(AMOUNT);
            assertThat(gateway.captured()).isEmpty();
            assertThat(inner.fetchCaptureState(REF_A)).isEqualTo(CaptureState.NOT_CAPTURED);
        }

        @Test
        @DisplayName("応答が届かない指定をすると UNREACHABLE になるが、確定した注文 ID に入り、mock では確定している")
        void lostResponseCaptures() {
            // 準備：1 回応答を届かなくする
            var orderId = newOrderId();
            gateway.loseNextCaptureResponses(1);

            // 操作：確定する
            var result = gateway.capture(captureRequest(orderId, REF_A));

            // 確認：結果は届かないが、確定している
            assertThat(result).isEqualTo(CaptureResult.UNREACHABLE);
            assertThat(gateway.captured()).containsExactly(orderId.value());
            assertThat(inner.fetchCaptureState(REF_A)).isEqualTo(CaptureState.CAPTURED);
        }

        @Test
        @DisplayName("期限切れにした与信は AUTHORIZATION_EXPIRED になって確定せず、他の与信は確定できる")
        void expiredAuthorizationIsNotCaptured() {
            // 準備：A を期限切れにする
            var expiredOrder = newOrderId();
            gateway.expireAuthorization(REF_A);

            // 操作：A と B を確定する
            var expired = gateway.capture(captureRequest(expiredOrder, REF_A));
            var other = gateway.capture(captureRequest(newOrderId(), REF_B));

            // 確認：A は確定せず、B は確定する
            assertThat(expired).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
            assertThat(inner.fetchCaptureState(REF_A)).isEqualTo(CaptureState.NOT_CAPTURED);
            assertThat(other).isEqualTo(CaptureResult.CAPTURED);

            // 確認：呼ばれた回数と金額には入り、確定した注文 ID と与信の参照には入らない
            assertThat(gateway.captureCallsOf(expiredOrder.value())).isEqualTo(1);
            assertThat(gateway.amountsOf(Operation.CAPTURE, expiredOrder.value())).containsExactly(AMOUNT);
            assertThat(gateway.captured()).doesNotContain(expiredOrder.value());
            assertThat(gateway.capturedAuthorizationRef(expiredOrder.value())).isEmpty();
        }

        @Test
        @DisplayName("接続できない指定と期限切れの両方では、接続できないが先で、回数を使い切ると期限切れになる")
        void unreachableComesBeforeExpiry() {
            // 準備：1 回接続できなくし、同じ与信を期限切れにする
            gateway.failNextCaptures(1);
            gateway.expireAuthorization(REF_A);

            // 操作：2 回確定する
            var first = gateway.capture(captureRequest(newOrderId(), REF_A));
            var second = gateway.capture(captureRequest(newOrderId(), REF_A));

            // 確認：先に接続できない、次に期限切れ
            assertThat(first).isEqualTo(CaptureResult.UNREACHABLE);
            assertThat(second).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("期限切れの回では、応答が届かない指定の回数は減らない")
        void expiryDoesNotConsumeLostResponses() {
            // 準備：1 回応答を届かなくし、A を期限切れにする
            gateway.loseNextCaptureResponses(1);
            gateway.expireAuthorization(REF_A);

            // 操作：期限切れの与信、期限切れでない与信の順に確定する
            var expired = gateway.capture(captureRequest(newOrderId(), REF_A));
            var other = gateway.capture(captureRequest(newOrderId(), REF_B));

            // 確認：期限切れの回で減らなかったので、次の回で応答が届かない
            assertThat(expired).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
            assertThat(other).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("差し込んだ処理は、確定した後に 1 回だけ動く")
        void runsDuringNextCaptureOnceAfterCapturing() {
            // 準備：処理の中で、確定した注文 ID を読む
            var runs = new AtomicInteger();
            var seen = new ArrayList<List<UUID>>();
            gateway.duringNextCapture(() -> {
                runs.incrementAndGet();
                seen.add(gateway.captured());
            });
            var first = newOrderId();

            // 操作：2 つの注文を確定する
            gateway.capture(captureRequest(first, REF_A));
            gateway.capture(captureRequest(newOrderId(), REF_B));

            // 確認：1 回だけ動き、動いたときは 1 つ目の注文が確定していた
            assertThat(runs).hasValue(1);
            assertThat(seen).containsExactly(List.of(first.value()));
        }

        @Test
        @DisplayName("差し込んだ処理は、接続できない回と期限切れの回では動かない")
        void doesNotRunDuringNextCaptureOnUnreachableOrExpired() {
            // 準備：差し込んだ処理、1 回の接続できない指定、B の期限切れ
            var runs = new AtomicInteger();
            gateway.duringNextCapture(runs::incrementAndGet);
            gateway.failNextCaptures(1);
            gateway.expireAuthorization(REF_B);

            // 操作：接続できない回、期限切れの回を呼ぶ
            gateway.capture(captureRequest(newOrderId(), REF_A));
            gateway.capture(captureRequest(newOrderId(), REF_B));

            // 確認：動かない
            assertThat(runs).hasValue(0);

            // 操作：確定できる回を呼ぶ
            gateway.capture(captureRequest(newOrderId(), REF_A));

            // 確認：動く
            assertThat(runs).hasValue(1);
        }

        @Test
        @DisplayName("差し込んだ処理は、応答が届かない回でも動く")
        void runsDuringNextCaptureOnLostResponse() {
            // 準備：差し込んだ処理と、1 回の応答が届かない指定
            var runs = new AtomicInteger();
            gateway.duringNextCapture(runs::incrementAndGet);
            gateway.loseNextCaptureResponses(1);

            // 操作：確定する
            gateway.capture(captureRequest(newOrderId(), REF_A));

            // 確認：動く
            assertThat(runs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("売上確定の状態の取得")
    class FetchCaptureState {

        @Test
        @DisplayName("呼ばれた回数を、全体と与信ごとに、失敗を含めて数える")
        void countsCallsIncludingFailures() {
            // 準備：1 回の失敗
            gateway.failNextCaptureStateFetches(1);

            // 操作：A の失敗する回と成功する回、B の成功する回を呼ぶ
            assertUnavailable(() -> gateway.fetchCaptureState(REF_A));
            gateway.fetchCaptureState(REF_A);
            gateway.fetchCaptureState(REF_B);

            // 確認：全体と与信ごとの回数
            assertThat(gateway.fetchCaptureStateCalls()).isEqualTo(3);
            assertThat(gateway.fetchCaptureStateCallsOf(REF_A)).isEqualTo(2);
            assertThat(gateway.fetchCaptureStateCallsOf(REF_B)).isEqualTo(1);
        }

        @Test
        @DisplayName("確定していない与信を期限切れにすると AUTHORIZATION_EXPIRED になる")
        void reportsExpiredWhenNotCaptured() {
            // 準備：期限切れにした与信
            gateway.expireAuthorization(REF_A);

            // 操作と確認：期限切れ
            assertThat(gateway.fetchCaptureState(REF_A)).isEqualTo(CaptureState.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("確定した与信は、期限切れにしても CAPTURED のまま")
        void reportsCapturedEvenWhenExpired() {
            // 準備：確定した与信を、期限切れにする
            gateway.capture(captureRequest(newOrderId(), REF_A));
            gateway.expireAuthorization(REF_A);

            // 操作と確認：確定済み
            assertThat(gateway.fetchCaptureState(REF_A)).isEqualTo(CaptureState.CAPTURED);
        }

        @Test
        @DisplayName("差し込んだ処理は、状態を読んだ後に 1 回だけ動く")
        void runsDuringNextFetchOnceAfterReadingState() {
            // 準備：処理の中で売上を確定する
            var runs = new AtomicInteger();
            gateway.duringNextCaptureStateFetch(() -> {
                runs.incrementAndGet();
                gateway.capture(captureRequest(newOrderId(), REF_A));
            });

            // 操作：2 回読む
            var first = gateway.fetchCaptureState(REF_A);
            var second = gateway.fetchCaptureState(REF_A);

            // 確認：1 回目は処理の前の状態、2 回目は確定済み。処理は 1 回だけ動いた
            assertThat(first).isEqualTo(CaptureState.NOT_CAPTURED);
            assertThat(second).isEqualTo(CaptureState.CAPTURED);
            assertThat(runs).hasValue(1);
        }

        @Test
        @DisplayName("外部サービスのエラーになる回では、差し込んだ処理は動かない")
        void doesNotRunDuringNextFetchOnFailure() {
            // 準備：差し込んだ処理と、1 回の失敗
            var runs = new AtomicInteger();
            gateway.duringNextCaptureStateFetch(runs::incrementAndGet);
            gateway.failNextCaptureStateFetches(1);

            // 操作と確認：失敗する回は、外部サービスのエラーで、処理は動かない
            assertUnavailable(() -> gateway.fetchCaptureState(REF_A));
            assertThat(runs).hasValue(0);

            // 操作：成功する回を呼ぶ
            gateway.fetchCaptureState(REF_A);

            // 確認：動く
            assertThat(runs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("金額を呼ばれた順に記録し、同じ注文を 2 回返金すると、金額は 2 件、返金した注文 ID は 1 回になる")
        void recordsAmountsAndRefundedOnce() {
            // 準備：注文
            var orderId = newOrderId();

            // 操作：同じ注文を、違う金額で 2 回返金する
            gateway.refund(new RefundRequest(orderId, REF_A, AMOUNT));
            gateway.refund(new RefundRequest(orderId, REF_A, OTHER_AMOUNT));

            // 確認：金額は呼ばれた順に 2 件、返金した注文 ID は 1 回。mock には 2 回届く
            assertThat(gateway.amountsOf(Operation.REFUND, orderId.value())).containsExactly(AMOUNT, OTHER_AMOUNT);
            assertThat(gateway.refunded()).containsExactly(orderId.value());
            assertThat(gateway.refundsOf(orderId.value())).isEqualTo(1);
            verify(inner, times(2)).refund(any());
        }

        @Test
        @DisplayName("失敗を指定すると予期しないエラーになり、金額は記録するが、返金した注文 ID に入らず mock に届かない")
        void failsWithoutRefunding() {
            // 準備：1 回の失敗
            var orderId = newOrderId();
            gateway.failNextRefunds(1);

            // 操作と確認：予期しないエラー
            assertSystemFailure(() -> gateway.refund(new RefundRequest(orderId, REF_A, AMOUNT)));

            // 確認：金額は記録され、返金は記録されず、mock に届かない
            assertThat(gateway.amountsOf(Operation.REFUND, orderId.value())).containsExactly(AMOUNT);
            assertThat(gateway.refunded()).isEmpty();
            verify(inner, never()).refund(any());
        }

        @Test
        @DisplayName("差し込んだ処理は、返金と与信の取消が呼ばれるたびに動く")
        void runsOnRefundOrVoidEveryCall() {
            // 準備：呼ばれた時点の処理
            var runs = new AtomicInteger();
            gateway.onRefundOrVoid(runs::incrementAndGet);

            // 操作：返金を 2 回、与信の取消を 2 回呼ぶ
            gateway.refund(new RefundRequest(newOrderId(), REF_A, AMOUNT));
            gateway.refund(new RefundRequest(newOrderId(), REF_A, AMOUNT));
            gateway.voidAuthorization(REF_A);
            gateway.voidAuthorization(REF_B);

            // 確認：4 回動く
            assertThat(runs).hasValue(4);
        }

        @Test
        @DisplayName("差し込んだ処理は、失敗する回でも先に動く")
        void runsOnRefundOrVoidBeforeFailure() {
            // 準備：処理の中で、返金した注文 ID を読む。1 回の失敗
            var seen = new ArrayList<List<UUID>>();
            gateway.onRefundOrVoid(() -> seen.add(gateway.refunded()));
            gateway.failNextRefunds(1);
            var orderId = newOrderId();

            // 操作と確認：失敗する
            assertSystemFailure(() -> gateway.refund(new RefundRequest(orderId, REF_A, AMOUNT)));

            // 確認：処理は動き、そのときは返金した注文 ID に入っていない
            assertThat(seen).containsExactly(List.of());
        }
    }

    @Nested
    @DisplayName("支払画面の操作")
    class PageOperations {

        @Test
        @DisplayName("pay すると、画面が支払済みになり、通知の port に届く")
        void payMarksPaidAndNotifies() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作：支払う
            gateway.pay(pageId);

            // 確認：支払済みで、通知が届く
            assertThat(gateway.page(pageId)).get().extracting(MockPaymentGateway.Page::status)
                    .isEqualTo(MockPaymentGateway.PageStatus.PAID);
            verify(receiver).authorized(pageId, gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("markPaid すると、画面が支払済みになるが、通知は届かない")
        void markPaidDoesNotNotify() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作：支払を済ませる
            gateway.markPaid(pageId);

            // 確認：支払済みで、通知は届かない
            assertThat(gateway.page(pageId)).get().extracting(MockPaymentGateway.Page::status)
                    .isEqualTo(MockPaymentGateway.PageStatus.PAID);
            verifyNoInteractions(receiver);
        }

        @Test
        @DisplayName("notifyAuthorized すると、通知の port に届くが、画面は支払済みにならない")
        void notifyAuthorizedOnlyNotifies() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作：通知する
            gateway.notifyAuthorized(pageId);

            // 確認：通知は届き、画面は開いたまま
            verify(receiver).authorized(pageId, gateway.authorizationRefOf(pageId));
            assertThat(gateway.page(pageId)).get().extracting(MockPaymentGateway.Page::status)
                    .isEqualTo(MockPaymentGateway.PageStatus.OPEN);
        }

        @Test
        @DisplayName("page は包んだ mock の画面を返し、知らない画面は空になる")
        void pageReturnsMockPage() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作と確認：mock と同じ画面。知らない画面は空
            assertThat(gateway.page(pageId)).isPresent().isEqualTo(inner.page(pageId));
            assertThat(gateway.page(UNKNOWN_PAGE_ID)).isEqualTo(Optional.empty());
        }

        @Test
        @DisplayName("authorizationRefOf は包んだ mock と同じ与信の参照を返す")
        void authorizationRefOfMatchesMock() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作と確認：mock と同じ参照
            assertThat(gateway.authorizationRefOf(pageId)).isEqualTo(inner.authorizationRefOf(pageId));
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("全ての記録が空になる")
        void clearsAllRecords() {
            // 準備：全ての操作を 1 回ずつ呼んで、記録を作る
            var orderId = newOrderId();
            var pageId = paidPage();
            gateway.closePage(pageId);
            gateway.fetchAuthorization(pageId);
            gateway.voidAuthorization(REF_A);
            gateway.capture(new CaptureRequest(orderId, REF_B, AMOUNT));
            gateway.fetchCaptureState(REF_B);
            gateway.refund(new RefundRequest(orderId, REF_B, AMOUNT));

            // 操作：初期化する
            gateway.reset();

            // 確認：全ての読み取りが空
            assertThat(gateway.openedPages()).isZero();
            assertThat(gateway.openedPageIds()).isEmpty();
            assertThat(gateway.closeCallsOf(pageId)).isZero();
            assertThat(gateway.fetchAuthorizationCalls()).isZero();
            assertThat(gateway.voided()).isEmpty();
            assertThat(gateway.voidsOf(REF_A)).isZero();
            assertThat(gateway.captureCalls()).isZero();
            assertThat(gateway.captureCallsOf(orderId.value())).isZero();
            assertThat(gateway.captured()).isEmpty();
            assertThat(gateway.capturedAuthorizationRef(orderId.value())).isEmpty();
            assertThat(gateway.fetchCaptureStateCalls()).isZero();
            assertThat(gateway.fetchCaptureStateCallsOf(REF_B)).isZero();
            assertThat(gateway.refunded()).isEmpty();
            assertThat(gateway.refundsOf(orderId.value())).isZero();
            for (var operation : Operation.values()) {
                assertThat(gateway.amountsOf(operation, orderId.value())).isEmpty();
            }
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("failureScenarios")
        @DisplayName("回数で指定した失敗が消える")
        void clearsCountedFailures(Scenario scenario) {
            // 準備：失敗を 2 回指定する
            scenario.arm().accept(gateway);

            // 確認（前提）：指定した失敗が有効になっている
            assertThat(scenario.act().test(gateway)).isFalse();

            // 操作：初期化する
            gateway.reset();

            // 確認：残りの失敗が消え、失敗せずに呼べる
            assertThat(scenario.act().test(gateway)).isTrue();
        }

        static Stream<Scenario> failureScenarios() {
            return Stream.of(
                    new Scenario("支払画面の作成",
                            g -> g.failNextOpens(2),
                            g -> succeeds(() -> g.openPage(pageRequest(newOrderId())))),
                    new Scenario("画面を閉じる",
                            g -> g.failNextCloses(2),
                            g -> succeeds(() -> g.closePage(UNKNOWN_PAGE_ID))),
                    new Scenario("与信の取得",
                            g -> g.failNextAuthorizationFetches(2),
                            g -> succeeds(() -> g.fetchAuthorization(paidPage(g)))),
                    new Scenario("与信の取消",
                            g -> g.failNextVoids(2),
                            g -> succeeds(() -> g.voidAuthorization(REF_A))),
                    new Scenario("売上確定の接続できない指定",
                            g -> g.failNextCaptures(2),
                            g -> g.capture(captureRequest(newOrderId(), REF_A)) == CaptureResult.CAPTURED),
                    new Scenario("売上確定の応答が届かない指定",
                            g -> g.loseNextCaptureResponses(2),
                            g -> g.capture(captureRequest(newOrderId(), REF_A)) == CaptureResult.CAPTURED),
                    new Scenario("売上確定の状態の取得",
                            g -> g.failNextCaptureStateFetches(2),
                            g -> succeeds(() -> g.fetchCaptureState(REF_A))),
                    new Scenario("返金",
                            g -> g.failNextRefunds(2),
                            g -> succeeds(() -> g.refund(new RefundRequest(newOrderId(), REF_A, AMOUNT)))));
        }

        @Test
        @DisplayName("与信の取得の拒否の指定が消える")
        void clearsRejection() {
            // 準備：拒否する画面
            var pageId = paidPage();
            gateway.rejectAuthorizationFetch(pageId);

            // 確認（前提）：拒否が有効になっている
            assertSystemFailure(() -> gateway.fetchAuthorization(pageId));

            // 操作：初期化する
            gateway.reset();

            // 確認：取得できる
            assertThat(gateway.fetchAuthorization(pageId).authorizationRef()).isEqualTo(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("与信の期限切れの指定が消える")
        void clearsExpiredAuthorizations() {
            // 準備：期限切れにした与信
            gateway.expireAuthorization(REF_A);

            // 確認（前提）：期限切れが有効になっている
            assertThat(gateway.fetchCaptureState(REF_A)).isEqualTo(CaptureState.AUTHORIZATION_EXPIRED);
            assertThat(gateway.capture(captureRequest(newOrderId(), REF_A))).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);

            // 操作：初期化する
            gateway.reset();

            // 確認：売上確定ができ、状態も期限切れにならない
            assertThat(gateway.fetchCaptureState(REF_A)).isEqualTo(CaptureState.NOT_CAPTURED);
            assertThat(gateway.capture(captureRequest(newOrderId(), REF_A))).isEqualTo(CaptureResult.CAPTURED);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("injectionScenarios")
        @DisplayName("差し込んだ処理が消える")
        void clearsInjectedActions(Injection injection) {
            // 準備：処理を差し込んで、1 回呼ぶ
            var runs = new AtomicInteger();
            injection.arm().accept(gateway, runs::incrementAndGet);
            injection.act().accept(gateway);

            // 確認（前提）：差し込んだ処理が動いた
            assertThat(runs).hasValue(1);

            // 操作：もう一度差し込み、初期化して、操作を呼ぶ
            injection.arm().accept(gateway, runs::incrementAndGet);
            gateway.reset();
            injection.act().accept(gateway);

            // 確認：処理は増えない
            assertThat(runs).hasValue(1);
        }

        static Stream<Injection> injectionScenarios() {
            return Stream.of(
                    new Injection("支払画面の作成の最中",
                            TestPaymentGateway::duringNextOpen,
                            g -> g.openPage(pageRequest(newOrderId()))),
                    new Injection("売上確定の最中",
                            TestPaymentGateway::duringNextCapture,
                            g -> g.capture(captureRequest(newOrderId(), REF_A))),
                    new Injection("売上確定の状態の取得の最中",
                            TestPaymentGateway::duringNextCaptureStateFetch,
                            g -> g.fetchCaptureState(REF_A)),
                    new Injection("返金の呼ばれた時点",
                            TestPaymentGateway::onRefundOrVoid,
                            g -> g.refund(new RefundRequest(newOrderId(), REF_A, AMOUNT))),
                    new Injection("与信の取消の呼ばれた時点",
                            TestPaymentGateway::onRefundOrVoid,
                            g -> g.voidAuthorization(REF_A)),
                    new Injection("与信の取得の呼ばれた時点",
                            TestPaymentGateway::onFetchAuthorization,
                            g -> g.fetchAuthorization(paidPage(g))));
        }

        @Test
        @DisplayName("包む mock の画面は残る")
        void keepsMockPages() {
            // 準備：開いた画面
            var pageId = openPage(newOrderId());

            // 操作：初期化する
            gateway.reset();

            // 確認：mock の画面は残る
            assertThat(gateway.page(pageId)).isPresent();
        }

        @Test
        @DisplayName("消す状態は 1 つの項目にまとまっていて、reset で消し忘れる項目がない")
        void keepsResettableStateInSingleField() {
            // 操作：インスタンスの項目の名前を集める（static の項目と合成の項目を除く）
            var fieldNames = Arrays.stream(TestPaymentGateway.class.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                    .map(Field::getName)
                    .toList();

            // 確認：包む mock と、reset で入れ替える状態だけ。項目を直接加えたときは、state に移す（reset が入れ替えるのは state だけで、直接の項目は消し忘れる）
            assertThat(fieldNames).containsExactlyInAnyOrder("mock", "state");
        }

        @Test
        @DisplayName("取消の差し込みの中で初期化しても、その取消は新しい記録に残る")
        void recordsVoidIntoNewStateWhenResetInsideInjection() {
            // 準備：取消の記録より前に動く処理の中で、初期化する
            gateway.onRefundOrVoid(gateway::reset);

            // 操作：取り消す
            gateway.voidAuthorization(REF_A);

            // 確認：初期化の後の記録に、その取消が入る
            assertThat(gateway.voided()).containsExactly(REF_A);
        }

        @Test
        @DisplayName("取消の差し込みの中で初期化した後に失敗を指定すると、その取消が失敗する")
        void appliesFailureSetInsideInjectionToSameCall() {
            // 準備：処理の中で、初期化してから 1 回の失敗を指定する
            gateway.onRefundOrVoid(() -> {
                gateway.reset();
                gateway.failNextVoids(1);
            });

            // 操作と確認：その取消が失敗する
            assertSystemFailure(() -> gateway.voidAuthorization(REF_A));

            // 確認：記録に入らない
            assertThat(gateway.voided()).isEmpty();
        }
    }

    @Nested
    @DisplayName("返した一覧")
    class ReturnedLists {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("listScenarios")
        @DisplayName("後の呼び出しで変わらない")
        void doesNotChangeAfterLaterCalls(ListScenario scenario) {
            // 準備：1 件を記録して、一覧を受け取る
            scenario.add().accept(gateway);
            var returned = scenario.read().apply(gateway);
            var sizeBefore = returned.size();

            // 操作：さらに 1 件を記録する
            scenario.add().accept(gateway);

            // 確認：受け取った一覧は変わらず、新しく読むと増えている
            assertThat(returned).hasSize(sizeBefore);
            assertThat(scenario.read().apply(gateway)).hasSize(sizeBefore + 1);
        }

        static Stream<ListScenario> listScenarios() {
            return Stream.of(
                    new ListScenario("確定した注文 ID",
                            TestPaymentGateway::captured,
                            g -> g.capture(captureRequest(newOrderId(), "mock_auth_" + UUID.randomUUID()))),
                    new ListScenario("返金した注文 ID",
                            TestPaymentGateway::refunded,
                            g -> g.refund(new RefundRequest(newOrderId(), REF_A, AMOUNT))),
                    new ListScenario("取り消した与信の参照",
                            TestPaymentGateway::voided,
                            g -> g.voidAuthorization("mock_auth_" + UUID.randomUUID())),
                    new ListScenario("作った支払画面の ID",
                            TestPaymentGateway::openedPageIds,
                            g -> g.openPage(pageRequest(newOrderId()))));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 処理が、外部サービスのエラーで失敗することを確認する。
     *
     * @param callable 処理
     */
    private static void assertUnavailable(ThrowingCallable callable) {
        assertSystemError(callable, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    /**
     * 処理が、予期しないエラーで失敗することを確認する。
     *
     * @param callable 処理
     */
    private static void assertSystemFailure(ThrowingCallable callable) {
        assertSystemError(callable, CommonErrorCode.SYSTEM_ERROR);
    }

    /**
     * 処理が失敗せずに終わったかを返す。
     *
     * @param action 処理
     * @return 例外なく終わったら true、システムのエラーで失敗したら false
     */
    private static boolean succeeds(Runnable action) {
        try {
            action.run();
            return true;
        } catch (SystemException e) {
            return false;
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 新しい注文 ID を作る。
     *
     * @return 注文 ID
     */
    private static OrderId newOrderId() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * 支払画面のリクエストを作る。
     *
     * @param orderId 注文 ID
     * @return リクエスト
     */
    private static PageRequest pageRequest(OrderId orderId) {
        return new PageRequest(new PaymentId(UUID.randomUUID()), orderId, AMOUNT, RETURN_URL);
    }

    /**
     * 売上確定のリクエストを作る。
     *
     * @param orderId          注文 ID
     * @param authorizationRef 与信の参照
     * @return リクエスト
     */
    private static CaptureRequest captureRequest(OrderId orderId, String authorizationRef) {
        return new CaptureRequest(orderId, authorizationRef, AMOUNT);
    }

    /**
     * 支払画面を開く。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    private String openPage(OrderId orderId) {
        return gateway.openPage(pageRequest(orderId)).id();
    }

    /**
     * 支払の済んだ支払画面を作る。
     *
     * @return 支払画面 ID
     */
    private String paidPage() {
        return paidPage(gateway);
    }

    /**
     * 指定した決済サービスで、支払の済んだ支払画面を作る。
     *
     * @param target 決済サービス
     * @return 支払画面 ID
     */
    private static String paidPage(TestPaymentGateway target) {
        var pageId = target.openPage(pageRequest(newOrderId())).id();
        target.markPaid(pageId);
        return pageId;
    }

    /**
     * 失敗の指定と、その指定が消えたことを確認する呼び出しの組
     *
     * @param name 表示する名前
     * @param arm  失敗を指定する操作
     * @param act  呼んで、失敗せずに成功したかを返す呼び出し
     */
    private record Scenario(String name, Consumer<TestPaymentGateway> arm, Predicate<TestPaymentGateway> act) {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 差し込む処理の指定と、その処理が動くはずだった呼び出しの組
     *
     * @param name 表示する名前
     * @param arm  処理を差し込む操作
     * @param act  処理が動くはずだった呼び出し
     */
    private record Injection(String name, BiConsumer<TestPaymentGateway, Runnable> arm, Consumer<TestPaymentGateway> act) {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 返す一覧の読み取りと、その一覧に 1 件を加える操作の組
     *
     * @param name 表示する名前
     * @param read 一覧を読む操作
     * @param add  一覧に 1 件を加える操作
     */
    private record ListScenario(String name, Function<TestPaymentGateway, List<?>> read, Consumer<TestPaymentGateway> add) {

        @Override
        public String toString() {
            return name;
        }
    }
}
